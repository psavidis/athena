import com.athena.git.TempDirectories;
import com.athena.reviewbriefing.BriefingItem;
import com.athena.reviewbriefing.FocusAreaGenerator;
import com.athena.reviewui.ChangeDetailView;
import com.athena.repository.ImportedPullRequest;
import com.athena.semantic.AnalysisResult;
import com.athena.semantic.Change;
import com.athena.semantic.ModuleGroup;
import com.athena.semantic.PrAnalyzer;
import com.athena.semantic.SemanticProfile;
import com.athena.semantic.SymbolAwareDiffEntry;
import com.athena.plugins.PluginRegistry;
import com.athena.web.ChangeKey;
import com.athena.web.Diff;
import com.athena.web.WebSession;
import com.athena.web.diff.DiffSelectionController;
import com.athena.web.reviewui.ChangeMapController;
import com.athena.web.reviewui.ModuleTopologyController;
import com.athena.web.reviewui.RepresentationCoverageController;
import com.athena.web.reviewui.SemanticProfileController;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures everything Athena's review UI would show for one corpus entry, as JSON, by driving
 * the real web controllers and view classes in-process — no Spring context, no browser.
 *
 * Read-only by construction: revisions are fetched with Athena's own `DiffSelectionController`
 * (a standalone Diff: `git fetch --depth 1 <public repo URL> <sha>`, no credentials), and no
 * GitHub token ever reaches the session, so none of Athena's GitHub write paths (comment/review
 * sync, resolve-thread, mark-viewed) can run. `ChangeMapController` insists on a "connected"
 * session plus a selected PR before it will answer, so the session is given a placeholder token
 * string that nothing in this program sends anywhere, and a PR record carrying only the title.
 *
 * Usage: java -cp <athena-app classpath> AthenaSnapshot.java <repo-url> <base-sha> <head-sha> <title> <out-dir>
 */
public class AthenaSnapshot {

    public static void main(String[] args) throws Exception {
        String repositoryUrl = args[0];
        String base = args[1];
        String head = args[2];
        String title = args[3];
        Path out = Path.of(args[4]);
        Files.createDirectories(out);

        ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        PrAnalyzer analyzer = new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins());
        WebSession session = new WebSession(analyzer);

        long start = System.nanoTime();
        new DiffSelectionController(session).createDiff(
                new DiffSelectionController.CreateDiffRequest(repositoryUrl, base, head));
        Diff diff = session.selectedDiff().orElseThrow();
        // createDiff already ran the analysis (it reports the Change count), so this is cached.
        List<Change> changes = diff.changes();
        long analyzed = System.nanoTime();

        // Re-home the same (already analyzed) Diff under a PR selection so ChangeMapController
        // will serve it. No GitHub call is made: the controller only reads the title from this record.
        Diff prDiff = diff;
        session.clearSelectedDiff();
        session.connect("placeholder-never-sent");
        session.select(new WebSession.SelectedPullRequest(
                new ImportedPullRequest(0, title, "", base, head, List.of(), List.of()), "corpus", prDiff));

        json.writeValue(out.resolve("change-map.json").toFile(), new ChangeMapController(session).changeMap());
        json.writeValue(out.resolve("semantic-profile.json").toFile(),
                new SemanticProfileController(session).pullRequestSemanticProfile());
        json.writeValue(out.resolve("topology.json").toFile(), new ModuleTopologyController(session).topology());
        json.writeValue(out.resolve("unrepresented-files.json").toFile(),
                new RepresentationCoverageController(session).unrepresentedFiles());

        List<ModuleGroup> modules = prDiff.moduleGroups();
        Map<String, Object> moduleProfiles = new LinkedHashMap<>();
        for (ModuleGroup module : modules) {
            moduleProfiles.put(module.moduleName(),
                    new SemanticProfileController(session).moduleSemanticProfile(module.moduleName()));
        }
        json.writeValue(out.resolve("module-profiles.json").toFile(), moduleProfiles);

        List<SemanticProfile> profiles = changes.stream().map(prDiff::semanticProfileFor).toList();
        List<Map<String, Object>> focusAreas = new ArrayList<>();
        for (BriefingItem item : new FocusAreaGenerator().generate(changes, profiles)) {
            focusAreas.add(Map.of("description", item.description(), "entity", item.entityReference().orElse("")));
        }
        json.writeValue(out.resolve("focus-areas.json").toFile(), focusAreas);

        List<Map<String, Object>> details = new ArrayList<>();
        for (Change change : changes) {
            ChangeDetailView view = ChangeDetailView.of(change);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("changeKey", ChangeKey.encode(change));
            detail.put("kind", view.kind());
            detail.put("category", view.category());
            detail.put("description", view.description());
            detail.put("files", view.files());
            detail.put("symbols", view.symbols());
            detail.put("occurrences", change.occurrenceCount());
            detail.put("exceptions", change.exceptionCount());
            detail.put("testCode", change.isTestCode());
            details.add(detail);
        }
        json.writeValue(out.resolve("changes.json").toFile(), details);

        // The analysis status and the files that fell back to a symbol-aware/textual diff are
        // not served by any endpoint, but they tell "Athena saw nothing" apart from "Athena
        // could not parse". Diff keeps its AnalysisResult private, so read it reflectively.
        Method analysisResult = Diff.class.getDeclaredMethod("analysisResult");
        analysisResult.setAccessible(true);
        AnalysisResult analysis = (AnalysisResult) analysisResult.invoke(diff);
        List<Map<String, String>> degraded = new ArrayList<>();
        for (SymbolAwareDiffEntry entry : analysis.symbolAwareDiffEntries()) {
            degraded.add(Map.of("file", entry.filePath(), "reason", entry.reason()));
        }

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("analysisStatus", analysis.status());
        run.put("degradedFiles", degraded);
        run.put("repositoryUrl", repositoryUrl);
        run.put("base", base);
        run.put("head", head);
        run.put("changeCount", changes.size());
        run.put("moduleCount", modules.size());
        run.put("checkoutAndAnalysisSeconds", Math.round((analyzed - start) / 1e7) / 100.0);
        json.writeValue(out.resolve("run.json").toFile(), run);

        System.out.println(json.writeValueAsString(run));
        TempDirectories.deleteRecursively(diff.workDir());
    }
}
