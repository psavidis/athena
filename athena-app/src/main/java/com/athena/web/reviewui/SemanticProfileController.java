package com.athena.web.reviewui;

import com.athena.semantic.Change;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.ModuleGroup;
import com.athena.semantic.ModuleGrouper;
import com.athena.semantic.SemanticClassification;
import com.athena.semantic.SemanticDimension;
import com.athena.semantic.SemanticProfile;
import com.athena.web.ChangeKey;
import com.athena.web.WebSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Serves a Change's full Semantic Profile (ticket #94) so the Semantic Change
 * Explorer (issue #91) has real data to render. Reuses {@link WebSession}'s
 * per-selection cached {@link com.athena.semantic.AnalysisResult} — this
 * controller never re-runs detection.
 *
 * <p>Whether a dimension's classifications are Observed or Inferred isn't
 * modeled anywhere yet (no classifier records this itself): {@link
 * #OBSERVED_DIMENSIONS} hardcodes it here from each classifier's own
 * documented behavior — {@code StructuralTaxonomyClassifier} is a direct,
 * deterministic reading of a transformation's kind, while the rest
 * (naming-convention correlation, {@code ResponsibilityTaxonomyClassifier}'s
 * kind-to-capability mapping, or Intent's cross-dimension correlation) are
 * explicitly heuristic — ticket #97 §"Capability level" calls out Capability
 * specifically as Inferred with a confidence percentage, not Observed.
 * Confidence for an Inferred entry is a flat placeholder — no scoring model
 * exists yet (out of scope per #91's "do not implement a complete autonomous
 * semantic/intent inference engine") — while Observed is always 100%, per
 * #91 §9's own example ("Structural: Observed · 100%"). INTENT is the one
 * exception: {@link com.athena.semantic.IntentTaxonomyClassifier} ranks a
 * primary reading plus any alternatives, and ticket #99 specifically calls
 * out that an alternative must never read as equally certain as the primary
 * — so INTENT alone degrades confidence per rank ({@link
 * #confidenceForRank}). Every other dimension keeps the flat placeholder
 * even when it happens to carry more than one entry (e.g. Pattern's two
 * simultaneously-recognized patterns, ticket #96) — that scenario was never
 * about ranking one classification above another, so its shipped behavior
 * stays unchanged here.
 */
@RestController
public class SemanticProfileController {

    private static final Set<SemanticDimension> OBSERVED_DIMENSIONS =
            EnumSet.of(SemanticDimension.STRUCTURAL);
    private static final Set<SemanticDimension> RANKED_CONFIDENCE_DIMENSIONS =
            EnumSet.of(SemanticDimension.INTENT);
    private static final int INFERRED_CONFIDENCE_PERCENT = 70;
    private static final int CONFIDENCE_STEP_DOWN_PER_RANK = 20;
    private static final int MINIMUM_CONFIDENCE_PERCENT = 10;

    private final WebSession session;

    public SemanticProfileController(WebSession session) {
        this.session = session;
    }

    @GetMapping("/api/review/change-map/{changeKey}/semantic-profile")
    public SemanticProfileResponse semanticProfile(@PathVariable String changeKey) {
        WebSession.SelectedPullRequest selection = requireSelection();
        Change change = requireChange(changeKey, selection.changes());
        return toResponse(List.of(selection.semanticProfileFor(change)));
    }

    /**
     * The Semantic Change Explorer aggregated across every Change in one module
     * (ticket #122's follow-up: the Explorer is the primary view for a module,
     * not a per-Change drill-down reached only after a flat change list). Simply
     * concatenates each Change's own entries per dimension — a module's Structure
     * level, say, is the union of every Change's structural entries, in the same
     * shape the frontend already groups single-Change entries in, so no new
     * response type or frontend grouping logic is needed for this to render.
     */
    @GetMapping("/api/review/modules/{moduleName}/semantic-profile")
    public SemanticProfileResponse moduleSemanticProfile(@PathVariable String moduleName) {
        WebSession.SelectedPullRequest selection = requireSelection();
        ModuleGroup group = requireModule(moduleName, selection.changes());
        List<SemanticProfile> profiles = group.changes().stream().map(selection::semanticProfileFor).toList();
        return toResponse(profiles);
    }

    /**
     * The Semantic Change Explorer aggregated across every Change in the whole
     * selected PR — the Explorer's landing scope the instant a PR is opened,
     * matching the approved mockup (#91's reference design): there is no
     * separate category/change-list screen in it, the Explorer itself is the
     * first thing a reviewer sees. Same merge as {@link #moduleSemanticProfile}
     * one level up: every Change in the PR instead of one module's.
     */
    @GetMapping("/api/review/semantic-profile")
    public SemanticProfileResponse pullRequestSemanticProfile() {
        WebSession.SelectedPullRequest selection = requireSelection();
        List<SemanticProfile> profiles = selection.changes().stream().map(selection::semanticProfileFor).toList();
        return toResponse(profiles);
    }

    private SemanticProfileResponse toResponse(List<SemanticProfile> profiles) {
        List<SemanticDimensionEntryResponse> entries = new ArrayList<>();
        for (SemanticDimension dimension : SemanticDimension.values()) {
            for (SemanticProfile profile : profiles) {
                List<SemanticClassification> classifications = profile.classifications(dimension);
                for (int rank = 0; rank < classifications.size(); rank++) {
                    entries.add(toEntry(dimension, classifications.get(rank), rank));
                }
            }
        }
        return new SemanticProfileResponse(entries);
    }

    private SemanticDimensionEntryResponse toEntry(SemanticDimension dimension, SemanticClassification classification,
                                                     int rank) {
        boolean inferred = !OBSERVED_DIMENSIONS.contains(dimension);
        int confidencePercent = inferred ? confidenceFor(dimension, rank) : 100;
        List<String> evidence = classification.evidence().stream().map(DetectedTransformation::diffText).toList();
        List<String> filesTouched = classification.evidence().stream()
                .flatMap(occurrence -> occurrence.filesTouched().stream())
                .distinct()
                .toList();
        return new SemanticDimensionEntryResponse(dimension, classification.concept().name(),
                classification.concept().description(), inferred, confidencePercent, evidence,
                classification.supportingConceptNames(), classification.beforeEvidenceCount(), filesTouched);
    }

    /**
     * The flat Inferred confidence for every dimension except {@link
     * #RANKED_CONFIDENCE_DIMENSIONS}, where the primary (rank 0) classification
     * keeps it and each alternative ranks lower.
     */
    private int confidenceFor(SemanticDimension dimension, int rank) {
        if (!RANKED_CONFIDENCE_DIMENSIONS.contains(dimension)) {
            return INFERRED_CONFIDENCE_PERCENT;
        }
        return Math.max(MINIMUM_CONFIDENCE_PERCENT, INFERRED_CONFIDENCE_PERCENT - rank * CONFIDENCE_STEP_DOWN_PER_RANK);
    }

    private WebSession.SelectedPullRequest requireSelection() {
        session.gitHubToken()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub"));
        return session.selectedPullRequest()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No PR selected"));
    }

    private Change requireChange(String changeKey, List<Change> changes) {
        return ChangeKey.resolve(changeKey, changes)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Change not found"));
    }

    private ModuleGroup requireModule(String moduleName, List<Change> changes) {
        return new ModuleGrouper().group(changes).stream()
                .filter(group -> group.moduleName().equals(moduleName))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found"));
    }
}
