package com.athena.web;

import com.athena.git.GitRevisionCheckout;
import com.athena.git.TempDirectories;
import com.athena.github.ImportedPullRequest;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewui.AnnotationBoard;
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

/**
 * Steps shared by the Change Map (ticket #74) and Change drill-down/annotations
 * (ticket #75) web feature files — both build on the same "reviewer has
 * selected a PR with a rename" fixture, so they share one glue class to
 * avoid Cucumber-JVM's duplicate-step-definition error across files.
 */
public class ChangeMapControllerSteps {

    private static final String PR_TITLE = "Move authentication to Account";
    private static final String REPOSITORY_FULL_NAME = "acme/widgets";

    private final WebSession session = new WebSession();
    private final ChangeMapController changeMapController = new ChangeMapController(session);
    private final ChangeDetailController detailController = new ChangeDetailController(session);

    private Path repoDir;
    private Path workDir;
    private ChangeMapResponse response;
    private String renameChangeKey;
    private ChangeDetailResponse detailResponse;
    private AnnotationsResponse annotationsResponse;
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
        session.select(new WebSession.SelectedPullRequest(
                pr, REPOSITORY_FULL_NAME, workDir, baseRoot, headRoot, new ReviewStateStore(), new AnnotationBoard(),
                new ReviewSubmission()));
    }

    @Given("the reviewer has not selected a PR")
    public void the_reviewer_has_not_selected_a_pr() {
        // no-op: a fresh WebSession starts with no selected PR
    }

    @Given("the reviewer has requested the Change Map")
    public void the_reviewer_has_requested_the_change_map() {
        ChangeMapResponse changeMap = changeMapController.changeMap();
        renameChangeKey = changeMap.changes().stream()
                .filter(entry -> entry.description().contains("Rename"))
                .findFirst()
                .orElseThrow()
                .changeKey();
    }

    @When("the reviewer requests the Change Map")
    public void the_reviewer_requests_the_change_map() {
        try {
            response = changeMapController.changeMap();
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

    @When("the reviewer requests the detail view of the rename Change")
    public void the_reviewer_requests_the_detail_view_of_the_rename_change() {
        requestDetailView(renameChangeKey);
    }

    @When("the reviewer requests the detail view of a Change key that does not match any current Change")
    public void the_reviewer_requests_the_detail_view_of_an_unmatched_change_key() {
        requestDetailView(ChangeKeyFixture.unmatched());
    }

    @When("the reviewer requests the detail view of any Change")
    public void the_reviewer_requests_the_detail_view_of_any_change() {
        requestDetailView(ChangeKeyFixture.unmatched());
    }

    private void requestDetailView(String changeKey) {
        try {
            detailResponse = detailController.changeDetail(changeKey);
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @Then("the detail response shows the Change's category {string}")
    public void the_detail_response_shows_the_changes_category(String category) {
        assertThat(detailResponse.category().name()).isEqualTo(category);
    }

    @Then("the detail response shows the Change's description")
    public void the_detail_response_shows_the_changes_description() {
        assertThat(detailResponse.description()).contains("Rename");
    }

    @Then("the detail response lists the involved symbols")
    public void the_detail_response_lists_the_involved_symbols() {
        assertThat(detailResponse.symbols()).isNotEmpty();
    }

    @Then("the detail response lists the touched files")
    public void the_detail_response_lists_the_touched_files() {
        assertThat(detailResponse.files()).contains("Greeter.java");
    }

    @Then("the detail response includes a diff field, present regardless of classification")
    public void the_detail_response_includes_a_diff_field_present_regardless_of_classification() {
        assertThat(detailResponse.diff()).isNotNull();
    }

    @Then("the request is rejected because the Change was not found")
    public void the_request_is_rejected_because_the_change_was_not_found() {
        assertThat(failure).isNotNull();
        assertThat(failure.getStatusCode().value()).isEqualTo(404);
    }

    @When("the reviewer posts the comment {string} scoped to the rename Change")
    public void the_reviewer_posts_a_comment_scoped_to_the_rename_change(String text) {
        annotationsResponse = detailController.addComment(new AddAnnotationRequest(changeScope(renameChangeKey), text));
    }

    @When("the reviewer posts the private note {string} scoped to the rename Change")
    public void the_reviewer_posts_a_private_note_scoped_to_the_rename_change(String text) {
        annotationsResponse =
                detailController.addPrivateNote(new AddAnnotationRequest(changeScope(renameChangeKey), text));
    }

    @When("the reviewer posts a blank comment scoped to the rename Change")
    public void the_reviewer_posts_a_blank_comment_scoped_to_the_rename_change() {
        try {
            detailController.addComment(new AddAnnotationRequest(changeScope(renameChangeKey), "   "));
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @Then("the response's comments include {string}")
    public void the_responses_comments_include(String text) {
        assertThat(annotationsResponse.comments()).contains(text);
    }

    @Then("the response's private notes include {string}")
    public void the_responses_private_notes_include(String text) {
        assertThat(annotationsResponse.privateNotes()).contains(text);
    }

    @Then("the response's comments do not include {string}")
    public void the_responses_comments_do_not_include(String text) {
        assertThat(annotationsResponse.comments()).doesNotContain(text);
    }

    @When("the reviewer posts the comment {string} scoped to line {int} of file {string}")
    public void the_reviewer_posts_a_comment_scoped_to_line_of_file(String text, int line, String filePath) {
        annotationsResponse = detailController.addComment(new AddAnnotationRequest(
                new AnnotationScopeRequest(AnnotationScopeRequest.ScopeType.LINE, filePath, line, null, null), text));
    }

    @When("the reviewer posts the comment {string} scoped to symbol {string}")
    public void the_reviewer_posts_a_comment_scoped_to_symbol(String text, String symbolDescription) {
        annotationsResponse = detailController.addComment(new AddAnnotationRequest(
                new AnnotationScopeRequest(AnnotationScopeRequest.ScopeType.SYMBOL, null, null, symbolDescription, null),
                text));
    }

    @When("the reviewer posts the comment {string} scoped to the whole review")
    public void the_reviewer_posts_a_comment_scoped_to_the_whole_review(String text) {
        annotationsResponse = detailController.addComment(new AddAnnotationRequest(
                new AnnotationScopeRequest(AnnotationScopeRequest.ScopeType.REVIEW, null, null, null, null), text));
    }

    @Then("the request is rejected as a bad request")
    public void the_request_is_rejected_as_a_bad_request() {
        assertThat(failure).isNotNull();
        assertThat(failure.getStatusCode().value()).isEqualTo(400);
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

    private AnnotationScopeRequest changeScope(String changeKey) {
        return new AnnotationScopeRequest(AnnotationScopeRequest.ScopeType.CHANGE, null, null, null, changeKey);
    }

    /** Exposed so other step classes sharing this fixture (ticket #76) can act on the same session/rename Change. */
    WebSession session() {
        return session;
    }

    String renameChangeKey() {
        return renameChangeKey;
    }

    /** Lets another step class record a failure so the shared "request is rejected..." Then steps above see it. */
    void recordFailure(ResponseStatusException e) {
        failure = e;
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
