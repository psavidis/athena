package com.athena.github;

import com.athena.reviewcontext.ReviewAnnotationSync;
import com.athena.reviewcontext.ReviewAnnotationSyncResult;
import com.athena.reviewui.AnnotationBoard;
import com.athena.reviewui.AnnotationScope;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

public class CommentNoteStorageAndSyncSteps {

    private final GitHubTestContext context;
    private final AnnotationBoard board = new AnnotationBoard();

    private String repositoryFullName;
    private int pullRequestNumber;
    private ReviewAnnotationSyncResult syncResult;

    public CommentNoteStorageAndSyncSteps(GitHubTestContext context) {
        this.context = context;
    }

    @Given("annotation sync targets repository {string} pull request {int}, which accepts synced comments")
    public void annotation_sync_targets_repository(String repo, int number) {
        repositoryFullName = repo;
        pullRequestNumber = number;
        context.token = "valid-token";
        context.transport.acceptToken(context.token, "octocat");
        context.transport.enableCommentSync(repo, number);
    }

    @Given("a comment {string} at review scope")
    public void a_comment_at_review_scope(String text) {
        board.addComment(AnnotationScope.review(), text);
    }

    @Given("a comment {string} at line {int} of file {string}")
    public void a_comment_at_line_of_file(String text, int line, String filePath) {
        board.addComment(AnnotationScope.line(filePath, line), text);
    }

    @Given("a private note {string} at review scope")
    public void a_private_note_at_review_scope(String text) {
        board.addPrivateNote(AnnotationScope.review(), text);
    }

    @When("the stored comments are synced to the pull request")
    public void the_stored_comments_are_synced_to_the_pull_request() {
        CommentSyncer syncer = new CommentSyncer(context.token, context.transport);
        syncResult = new ReviewAnnotationSync(syncer).syncComments(board, repositoryFullName, pullRequestNumber);
    }

    @Then("the pull request has a synced general comment {string}")
    public void the_pull_request_has_a_synced_general_comment(String expectedBody) {
        assertThat(context.transport.postedGeneralComments(repositoryFullName, pullRequestNumber))
                .contains(expectedBody);
    }

    @Then("the pull request has a synced line comment {string} on file {string} line {int}")
    public void the_pull_request_has_a_synced_line_comment(String expectedBody, String path, int line) {
        assertThat(context.transport.postedLineComments(repositoryFullName, pullRequestNumber))
                .anyMatch(comment -> comment.body().equals(expectedBody)
                        && comment.path().equals(path) && comment.line() == line);
    }

    @Then("the pull request has no synced comment mentioning {string}")
    public void the_pull_request_has_no_synced_comment_mentioning(String text) {
        assertThat(context.transport.postedGeneralComments(repositoryFullName, pullRequestNumber))
                .noneMatch(comment -> comment.contains(text));
        assertThat(context.transport.postedLineComments(repositoryFullName, pullRequestNumber))
                .noneMatch(comment -> comment.body().contains(text));
    }

    @Then("the pull request has no synced general comments")
    public void the_pull_request_has_no_synced_general_comments() {
        assertThat(context.transport.postedGeneralComments(repositoryFullName, pullRequestNumber)).isEmpty();
    }

    @Then("the sync result reports every comment synced successfully")
    public void the_sync_result_reports_every_comment_synced_successfully() {
        assertThat(syncResult.isFullySuccessful()).isTrue();
        assertThat(syncResult.failed()).isEmpty();
    }
}
