package com.athena.github;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

public class SyncReviewDecisionToGitHubSteps {

    private final GitHubTestContext context;
    private String repositoryFullName;
    private int pullRequestNumber;
    private boolean syncSucceeded;
    private Exception thrown;

    public SyncReviewDecisionToGitHubSteps(GitHubTestContext context) {
        this.context = context;
    }

    @Given("repository {string} pull request {int} accepts synced reviews")
    public void repository_accepts_synced_reviews(String repo, int number) {
        repositoryFullName = repo;
        pullRequestNumber = number;
        context.transport.enableReviewSync(repo, number);
    }

    @When("Athena syncs an approval {string} to pull request {int} in repository {string}")
    public void athena_syncs_approval(String body, int number, String repo) {
        ReviewDecisionSyncer syncer = new ReviewDecisionSyncer(context.token, context.transport);
        try {
            syncer.syncApproval(repo, number, body);
            syncSucceeded = true;
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("Athena syncs a request for changes {string} to pull request {int} in repository {string}")
    public void athena_syncs_request_changes(String body, int number, String repo) {
        ReviewDecisionSyncer syncer = new ReviewDecisionSyncer(context.token, context.transport);
        try {
            syncer.syncRequestChanges(repo, number, body);
            syncSucceeded = true;
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("Athena syncs an approval {string} to pull request {int} in repository {string} twice")
    public void athena_syncs_approval_twice(String body, int number, String repo) {
        ReviewDecisionSyncer syncer = new ReviewDecisionSyncer(context.token, context.transport);
        try {
            syncer.syncApproval(repo, number, body);
            syncer.syncApproval(repo, number, body);
            syncSucceeded = true;
        } catch (Exception e) {
            thrown = e;
        }
    }

    @Then("the review sync succeeds")
    public void review_sync_succeeds() {
        assertThat(syncSucceeded).isTrue();
        assertThat(thrown).isNull();
    }

    @Then("GitHub shows a {string} review {string} on pull request {int}")
    public void github_shows_review(String event, String body, int number) {
        assertThat(context.transport.postedReviews(repositoryFullName, number))
                .anySatisfy(r -> {
                    assertThat(r.event()).isEqualTo(event);
                    assertThat(r.body()).isEqualTo(body);
                });
    }

    @Then("GitHub shows exactly one {string} review {string} on pull request {int}")
    public void github_shows_exactly_one_review(String event, String body, int number) {
        assertThat(context.transport.postedReviews(repositoryFullName, number))
                .containsExactly(new FakeGitHubTransport.PostedReview(event, body));
    }

    @Then("the review sync fails clearly")
    public void review_sync_fails_clearly() {
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isNotBlank();
    }
}
