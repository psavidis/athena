package com.athena.web;

import com.athena.git.GitRevisionCheckout;
import com.athena.git.TempDirectories;
import com.athena.github.ImportedPullRequest;
import com.athena.semantic.ChangeCategory;
import com.athena.semantic.ReviewStateStore;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ChangeMapControllerSteps {

    private static final String PR_TITLE = "Move authentication to Account";

    private final WebSession session = new WebSession();
    private final ChangeMapController controller = new ChangeMapController(session);

    private Path repoDir;
    private Path workDir;
    private ChangeMapResponse response;
    private ResponseStatusException failure;

    @Before
    public void createTempRoots() throws IOException {
        repoDir = Files.createTempDirectory("athena-change-map-repo-");
        workDir = Files.createTempDirectory("athena-change-map-work-");
    }

    @After
    public void cleanUpTempRoots() {
        TempDirectories.deleteRecursively(repoDir);
        TempDirectories.deleteRecursively(workDir);
    }

    @Given("the reviewer is connected to GitHub")
    public void the_reviewer_is_connected_to_github() {
        session.connect("test-token");
    }

    @Given("the reviewer has not connected to GitHub")
    public void the_reviewer_has_not_connected_to_github() {
        // no-op: a fresh WebSession starts with no token
    }

    @Given("the reviewer has selected a PR whose base and head revisions differ by a rename")
    public void the_reviewer_has_selected_a_pr_differing_by_a_rename() throws IOException, InterruptedException {
        initRepo();
        writeFile("Greeter.java", "public class Greeter {\n"
                + "    public String greet() {\n"
                + "        return \"hi\";\n"
                + "    }\n"
                + "}\n");
        String baseSha = commit("Add Greeter");

        writeFile("Greeter.java", "public class Greeter {\n"
                + "    public String salute() {\n"
                + "        return \"hi\";\n"
                + "    }\n"
                + "}\n");
        String headSha = commit("Rename greet to salute");

        ImportedPullRequest pr = new ImportedPullRequest(1, PR_TITLE, "author", baseSha, headSha, List.of(), List.of());
        Path baseRoot = GitRevisionCheckout.checkout(repoDir.toString(), baseSha, workDir, Map.of());
        Path headRoot = GitRevisionCheckout.checkout(repoDir.toString(), headSha, workDir, Map.of());
        session.select(new WebSession.SelectedPullRequest(pr, workDir, baseRoot, headRoot, new ReviewStateStore()));
    }

    @Given("the reviewer has not selected a PR")
    public void the_reviewer_has_not_selected_a_pr() {
        // no-op: a fresh WebSession starts with no selected PR
    }

    @When("the reviewer requests the Change Map")
    public void the_reviewer_requests_the_change_map() {
        try {
            response = controller.changeMap();
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @Then("the Change Map response includes the PR's title")
    public void the_change_map_response_includes_the_prs_title() {
        assertThat(response.prTitle()).isEqualTo(PR_TITLE);
    }

    @Then("the Change Map lists the rename Change under category {string}")
    public void the_change_map_lists_the_rename_change_under_category(String category) {
        assertThat(response.changes())
                .anySatisfy(entry -> {
                    assertThat(entry.category().name()).isEqualTo(category);
                    assertThat(entry.description()).contains("Rename");
                });
    }

    @Then("the Change Map's category counts show {int} Change under {string}")
    public void the_change_maps_category_counts_show_a_change_under(int expectedCount, String category) {
        assertThat(response.categoryCounts().get(ChangeCategory.valueOf(category)))
                .isEqualTo(expectedCount);
    }

    @Then("the request is rejected as unauthorized")
    public void the_request_is_rejected_as_unauthorized() {
        assertThat(failure).isNotNull();
        assertThat(failure.getStatusCode().value()).isEqualTo(401);
    }

    @Then("the request is rejected because no PR is selected")
    public void the_request_is_rejected_because_no_pr_is_selected() {
        assertThat(failure).isNotNull();
        assertThat(failure.getStatusCode().value()).isEqualTo(409);
    }

    private void initRepo() throws IOException, InterruptedException {
        run(repoDir, "git", "init", "--quiet");
        run(repoDir, "git", "config", "user.email", "test@example.com");
        run(repoDir, "git", "config", "user.name", "Test");
    }

    private void writeFile(String name, String content) throws IOException {
        Files.writeString(repoDir.resolve(name), content);
    }

    private String commit(String message) throws IOException, InterruptedException {
        run(repoDir, "git", "add", ".");
        run(repoDir, "git", "commit", "--quiet", "-m", message);
        return runAndCapture(repoDir, "git", "rev-parse", "HEAD").strip();
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
        Process process = new ProcessBuilder(command).directory(dir.toFile()).start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Command failed: " + String.join(" ", command));
        }
        return output;
    }
}
