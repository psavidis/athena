package com.athena.web.diff;

import com.athena.plugins.PluginRegistry;
import com.athena.repository.ImportedPullRequest;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.PrAnalyzer;
import com.athena.semantic.ReviewStateStore;
import com.athena.web.WebSession;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalDiffSteps {

    private final WebSession session = new WebSession(new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins()));
    private final DiffSelectionController controller = new DiffSelectionController(session);

    private Path repoDir;
    private Path secondRepoDir;
    private String baseSha;
    private String headSha;
    private DiffSelectionController.DiffResponse response;

    @Before
    public void createTempRoots() throws IOException {
        repoDir = Files.createTempDirectory("athena-local-diff-repo-");
    }

    @After
    public void cleanUpTempRoots() {
        deleteRecursively(repoDir);
        if (secondRepoDir != null) {
            deleteRecursively(secondRepoDir);
        }
    }

    @Given("a local repository whose two revisions differ by a renamed method")
    public void a_local_repository_whose_revisions_differ_by_a_rename() throws Exception {
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
    }

    @Given("a local repository whose two revisions are identical")
    public void a_local_repository_whose_revisions_are_identical() throws Exception {
        initRepo(repoDir);
        writeFile(repoDir, "Greeter.java", "public class Greeter {\n"
                + "    public String greet() {\n"
                + "        return \"hi\";\n"
                + "    }\n"
                + "}\n");
        baseSha = commit(repoDir, "Add Greeter");
        headSha = baseSha;
    }

    @Given("the user has already created a Diff from a local repository")
    public void the_user_has_already_created_a_diff() throws Exception {
        a_local_repository_whose_revisions_differ_by_a_rename();
        the_user_creates_a_diff_from_those_two_revisions();
    }

    @Given("the user has already created a standalone Diff")
    public void the_user_has_already_created_a_standalone_diff() throws Exception {
        the_user_has_already_created_a_diff();
    }

    @When("the user creates a second Diff from a different local repository")
    public void the_user_creates_a_second_diff_from_a_different_repository() throws Exception {
        secondRepoDir = Files.createTempDirectory("athena-local-diff-repo-2-");
        initRepo(secondRepoDir);
        writeFile(secondRepoDir, "Other.java", "public class Other {\n}\n");
        String sha = commit(secondRepoDir, "Add Other");
        controller.createDiff(new DiffSelectionController.CreateDiffRequest(secondRepoDir.toString(), sha, sha));
    }

    @Given("the user has a PR Review selected in the session")
    public void the_user_has_a_pr_review_selected() throws Exception {
        Path prWorkDir = Files.createTempDirectory("athena-local-diff-pr-work-");
        ImportedPullRequest pr = new ImportedPullRequest(1, "Some PR", "author", "base", "head", List.of(), List.of());
        session.select(new WebSession.SelectedPullRequest(
                pr, "acme/widgets", prWorkDir, prWorkDir, prWorkDir, new ReviewStateStore(),
                new AnnotationBoard(), new ReviewSubmission()));
    }

    @When("the user creates a Diff from those two revisions")
    public void the_user_creates_a_diff_from_those_two_revisions() {
        response = controller.createDiff(new DiffSelectionController.CreateDiffRequest(repoDir.toString(), baseSha, headSha));
    }

    @When("the user creates a Diff from a local repository")
    public void the_user_creates_a_diff_from_a_local_repository() throws Exception {
        a_local_repository_whose_revisions_are_identical();
        the_user_creates_a_diff_from_those_two_revisions();
    }

    @When("the user selects a PR Review in the session")
    public void the_user_selects_a_pr_review() throws Exception {
        the_user_has_a_pr_review_selected();
    }

    @Then("the Diff reports at least one detected Change")
    public void the_diff_reports_at_least_one_change() {
        assertThat(response.changeCount()).isGreaterThan(0);
    }

    @Then("the Diff reports no detected Changes")
    public void the_diff_reports_no_changes() {
        assertThat(response.changeCount()).isEqualTo(0);
    }

    @Then("the session's selected Diff is the second one")
    public void the_sessions_selected_diff_is_the_second_one() {
        assertThat(session.selectedDiff()).isPresent();
        assertThat(session.selectedDiff().get().baseRoot().toString()).doesNotContain(repoDir.getFileName().toString());
    }

    @Then("the session no longer has a PR Review selected")
    public void the_session_no_longer_has_a_pr_review_selected() {
        assertThat(session.selectedPullRequest()).isEmpty();
    }

    @Then("the session no longer has a standalone Diff selected")
    public void the_session_no_longer_has_a_standalone_diff_selected() {
        assertThat(session.selectedDiff()).isEmpty();
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
