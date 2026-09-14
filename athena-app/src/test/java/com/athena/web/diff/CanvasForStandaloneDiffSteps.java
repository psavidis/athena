package com.athena.web.diff;

import com.athena.plugins.PluginRegistry;
import com.athena.semantic.PrAnalyzer;
import com.athena.web.ChangeKey;
import com.athena.web.WebSession;
import com.athena.web.reviewui.CanvasCommentController;
import com.athena.web.reviewui.CanvasCommentRequest;
import com.athena.web.reviewui.CanvasCommentResponse;
import com.athena.web.reviewui.ChangeDetailController;
import com.athena.web.reviewui.ChangeDetailResponse;
import com.athena.web.reviewui.ModuleTopologyController;
import com.athena.web.reviewui.ModuleTopologyResponse;
import com.athena.web.reviewui.SemanticProfileController;
import com.athena.web.reviewui.SemanticProfileResponse;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for {@code canvas_for_a_standalone_diff.feature} (ticket #111/#153):
 * the Semantic Canvas's web-layer controllers (territory map, semantic
 * profile, Change detail, canvas comments) actually rendering for a
 * selected standalone {@link com.athena.web.Diff}, not only a selected PR
 * Review — the backend gap found while writing this ticket's Gherkin.
 */
public class CanvasForStandaloneDiffSteps {

    private final WebSession session = new WebSession(new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins()));
    private final DiffSelectionController diffController = new DiffSelectionController(session);
    private final ModuleTopologyController topologyController = new ModuleTopologyController(session);
    private final SemanticProfileController semanticProfileController = new SemanticProfileController(session);
    private final ChangeDetailController changeDetailController = new ChangeDetailController(session, () -> "alex");
    private final CanvasCommentController canvasCommentController = new CanvasCommentController(session, () -> "alex");

    private Path repoDir;
    private String baseSha;
    private String headSha;
    private ModuleTopologyResponse topologyResponse;
    private SemanticProfileResponse semanticProfileResponse;
    private ChangeDetailResponse changeDetailResponse;
    private List<CanvasCommentResponse> commentsResponse;

    @Before
    public void createTempRoot() throws IOException {
        repoDir = Files.createTempDirectory("athena-canvas-standalone-diff-repo-");
    }

    @After
    public void cleanUpTempRoot() {
        deleteRecursively(repoDir);
    }

    @Given("the user has created a standalone Diff whose revisions differ by a renamed method")
    public void the_user_has_created_a_standalone_diff() throws Exception {
        initRepo(repoDir);
        writeFile(repoDir, "Greeter.java", "public class Greeter {\n"
                + "    public String greet() {\n"
                + "        return \"hi\";\n"
                + "    }\n"
                + "}\n");
        baseSha = commit(repoDir, "Add Greeter");

        writeFile(repoDir, "Greeter.java", "public class Greeter {\n"
                + "    public String salute() {\n"
                + "        return \"hi\";\n"
                + "    }\n"
                + "}\n");
        headSha = commit(repoDir, "Rename greet to salute");

        diffController.createDiff(new DiffSelectionController.CreateDiffRequest(repoDir.toString(), baseSha, headSha));
    }

    @Given("the user is not connected to GitHub")
    public void the_user_is_not_connected_to_github() {
        // no-op: WebSession starts with no GitHub token by default
    }

    @When("the user requests the Semantic Canvas territory map")
    public void the_user_requests_the_territory_map() {
        topologyResponse = topologyController.topology();
    }

    @When("the user requests the semantic profile for that Diff's touched module")
    public void the_user_requests_the_semantic_profile() {
        String moduleName = topologyController.topology().territories().get(0).moduleName();
        semanticProfileResponse = semanticProfileController.moduleSemanticProfile(moduleName);
    }

    @When("the user requests that Diff's Change detail")
    public void the_user_requests_the_change_detail() {
        String changeKey = ChangeKey.encode(session.selectedDiff().orElseThrow().changes().get(0));
        changeDetailResponse = changeDetailController.changeDetail(changeKey);
    }

    @When("the user posts a comment on a canvas item in that Diff")
    public void the_user_posts_a_comment_on_a_canvas_item() {
        String moduleName = topologyController.topology().territories().get(0).moduleName();
        commentsResponse = canvasCommentController.addComment(
                "territory:" + moduleName, new CanvasCommentRequest("Why did this move?"));
    }

    @Then("the territory map reflects that Diff's detected Changes")
    public void the_territory_map_reflects_the_diffs_changes() {
        assertThat(topologyResponse.territories()).isNotEmpty();
        int totalChangeKeys = topologyResponse.territories().stream()
                .mapToInt(t -> t.changeKeys().size())
                .sum();
        assertThat(totalChangeKeys).isGreaterThan(0);
    }

    @Then("the semantic profile reflects that Diff's detected Changes")
    public void the_semantic_profile_reflects_the_diffs_changes() {
        assertThat(semanticProfileResponse.dimensions()).isNotEmpty();
    }

    @Then("the Change detail is returned")
    public void the_change_detail_is_returned() {
        assertThat(changeDetailResponse).isNotNull();
    }

    @Then("the comment appears in that item's comment thread")
    public void the_comment_appears_in_the_thread() {
        assertThat(commentsResponse).extracting(CanvasCommentResponse::text).contains("Why did this move?");
    }

    private void initRepo(Path dir) throws IOException, InterruptedException {
        run(dir, "git", "init", "--quiet");
        run(dir, "git", "config", "user.email", "test@example.com");
        run(dir, "git", "config", "user.name", "Test");
    }

    private void writeFile(Path dir, String relativePath, String content) throws IOException {
        Files.writeString(dir.resolve(relativePath), content);
    }

    private String commit(Path dir, String message) throws IOException, InterruptedException {
        run(dir, "git", "add", ".");
        run(dir, "git", "commit", "--quiet", "-m", message);
        return runAndCapture(dir, "git", "rev-parse", "HEAD").strip();
    }

    private void run(Path dir, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(dir.toFile()).start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String stderr = new String(process.getErrorStream().readAllBytes());
            throw new IllegalStateException("Command failed: " + String.join(" ", command) + "\n" + stderr);
        }
    }

    private String runAndCapture(Path dir, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(false).start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor();
        return output;
    }

    private void deleteRecursively(Path root) {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // best-effort cleanup of a temp directory
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup of a temp directory
        }
    }
}
