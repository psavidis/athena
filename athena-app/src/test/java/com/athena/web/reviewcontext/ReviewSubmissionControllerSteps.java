package com.athena.web.reviewcontext;

import com.athena.plugins.TestChanges;
import com.athena.github.FakeGitHubTransport;
import com.athena.reviewui.AnnotationScope;
import com.athena.semantic.Change;
import com.athena.semantic.ReviewState;
import com.athena.web.ChangeKey;
import com.athena.web.ChangeKeyFixture;
import com.athena.web.WebSession;
import com.athena.web.reviewui.ChangeMapControllerSteps;
import com.athena.web.reviewui.ChangeMapController;
import com.athena.web.reviewui.ChangeMapResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for review state, pre-submission summary & submission (ticket #76).
 * Reuses {@link ChangeMapControllerSteps}'s "reviewer has selected a PR with
 * a rename" fixture (same session, same rename Change) via constructor
 * injection, per its established shared-steps-class convention.
 */
public class ReviewSubmissionControllerSteps {

    private static final String TEST_TOKEN = "test-token";

    private final ChangeMapControllerSteps changeMapSteps;
    // Fake GitHub HTTP boundary: real network calls to api.github.com are an
    // external system boundary tests can't/shouldn't cross for real.
    private final FakeGitHubTransport transport = new FakeGitHubTransport();

    private ReviewSubmissionController controller;
    private PreSubmissionSummaryResponse summaryResponse;
    private SubmissionResponse submissionResponse;

    public ReviewSubmissionControllerSteps(ChangeMapControllerSteps changeMapSteps) {
        this.changeMapSteps = changeMapSteps;
    }

    private ReviewSubmissionController controller() {
        if (controller == null) {
            transport.acceptToken(TEST_TOKEN, "reviewer");
            controller = new ReviewSubmissionController(changeMapSteps.session(), transport);
        }
        return controller;
    }

    @Given("GitHub sync is enabled for that Pull Request")
    public void github_sync_is_enabled_for_that_pull_request() {
        WebSession.SelectedPullRequest selection = changeMapSteps.session().selectedPullRequest().orElseThrow();
        transport.enableCommentSync(selection.repositoryFullName(), selection.pullRequest().number());
        transport.enableReviewSync(selection.repositoryFullName(), selection.pullRequest().number());
    }

    @When("the reviewer sets the rename Change's review state to {string}")
    public void the_reviewer_sets_the_rename_changes_review_state(String state) {
        setReviewState(changeMapSteps.renameChangeKey(), state);
    }

    @Given("the reviewer has set the rename Change's review state to {string}")
    public void the_reviewer_has_set_the_rename_changes_review_state(String state) {
        setReviewState(changeMapSteps.renameChangeKey(), state);
    }

    @When("the reviewer sets the review state of a Change key that does not match any current Change")
    public void the_reviewer_sets_the_review_state_of_an_unmatched_change_key() {
        setReviewState(ChangeKeyFixture.unmatched(), "REVIEWED");
    }

    private void setReviewState(String changeKey, String state) {
        try {
            controller().setReviewState(changeKey, new SetReviewStateRequest(ReviewState.valueOf(state)));
        } catch (ResponseStatusException e) {
            changeMapSteps.recordFailure(e);
        }
    }

    @Given("the reviewer has posted the comment {string} scoped to the rename Change")
    public void the_reviewer_has_posted_a_comment_scoped_to_the_rename_change(String text) {
        changeMapSteps.session().selectedPullRequest().orElseThrow().annotationBoard()
                .addComment(AnnotationScope.change(resolveRenameChange()), text);
    }

    @Given("the reviewer has posted the private note {string} scoped to the rename Change")
    public void the_reviewer_has_posted_a_private_note_scoped_to_the_rename_change(String text) {
        changeMapSteps.session().selectedPullRequest().orElseThrow().annotationBoard()
                .addPrivateNote(AnnotationScope.change(resolveRenameChange()), text);
    }

    private Change resolveRenameChange() {
        WebSession.SelectedPullRequest selection = changeMapSteps.session().selectedPullRequest().orElseThrow();
        List<Change> changes = TestChanges.detect(selection.baseRoot(), selection.headRoot());
        return ChangeKey.resolve(changeMapSteps.renameChangeKey(), changes).orElseThrow();
    }

    @When("the reviewer requests the pre-submission summary via the API")
    public void the_reviewer_requests_the_pre_submission_summary_via_the_api() {
        try {
            summaryResponse = controller().preSubmissionSummary();
        } catch (ResponseStatusException e) {
            changeMapSteps.recordFailure(e);
        }
    }

    @When("the reviewer confirms and submits the review via the API")
    public void the_reviewer_confirms_and_submits_the_review_via_the_api() {
        try {
            submissionResponse = controller().submit();
        } catch (ResponseStatusException e) {
            changeMapSteps.recordFailure(e);
        }
    }

    @Then("the Change Map shows the rename Change's review state as {string}")
    public void the_change_map_shows_the_rename_changes_review_state(String expectedState) {
        ChangeMapResponse changeMap = new ChangeMapController(changeMapSteps.session()).changeMap();
        assertThat(changeMap.changes())
                .filteredOn(entry -> entry.changeKey().equals(changeMapSteps.renameChangeKey()))
                .singleElement()
                .satisfies(entry -> assertThat(entry.reviewState().name()).isEqualTo(expectedState));
    }

    @Then("the summary lists the rename Change among the reviewed Changes")
    public void the_summary_lists_the_rename_change_among_the_reviewed_changes() {
        assertThat(summaryResponse.reviewedChangeTitles()).anySatisfy(title -> assertThat(title).contains("Rename"));
    }

    @Then("the summary lists the rename Change among the concern Changes")
    public void the_summary_lists_the_rename_change_among_the_concern_changes() {
        assertThat(summaryResponse.concernChangeTitles()).anySatisfy(title -> assertThat(title).contains("Rename"));
    }

    @Then("the summary shows a comment count of {int}")
    public void the_summary_shows_a_comment_count_of(int count) {
        assertThat(summaryResponse.commentCount()).isEqualTo(count);
    }

    @Then("the API summary previews the GitHub action {string}")
    public void the_api_summary_previews_the_github_action(String action) {
        assertThat(summaryResponse.gitHubAction().name()).isEqualTo(action);
    }

    @Then("GitHub shows the posted comment {string}")
    public void github_shows_the_posted_comment(String text) {
        WebSession.SelectedPullRequest selection = changeMapSteps.session().selectedPullRequest().orElseThrow();
        assertThat(transport.postedGeneralComments(selection.repositoryFullName(), selection.pullRequest().number()))
                .contains(text);
    }

    @Then("GitHub shows no posted comments")
    public void github_shows_no_posted_comments() {
        WebSession.SelectedPullRequest selection = changeMapSteps.session().selectedPullRequest().orElseThrow();
        assertThat(transport.postedGeneralComments(selection.repositoryFullName(), selection.pullRequest().number()))
                .isEmpty();
    }

    @Then("GitHub shows a posted {string} review")
    public void github_shows_a_posted_review(String event) {
        WebSession.SelectedPullRequest selection = changeMapSteps.session().selectedPullRequest().orElseThrow();
        assertThat(transport.postedReviews(selection.repositoryFullName(), selection.pullRequest().number()))
                .anySatisfy(review -> assertThat(review.event()).isEqualTo(event));
    }

    @Then("GitHub shows no posted reviews")
    public void github_shows_no_posted_reviews() {
        WebSession.SelectedPullRequest selection = changeMapSteps.session().selectedPullRequest().orElseThrow();
        assertThat(transport.postedReviews(selection.repositoryFullName(), selection.pullRequest().number()))
                .isEmpty();
    }

    @Then("the submission response reports success")
    public void the_submission_response_reports_success() {
        assertThat(submissionResponse.fullySynced()).isTrue();
    }
}
