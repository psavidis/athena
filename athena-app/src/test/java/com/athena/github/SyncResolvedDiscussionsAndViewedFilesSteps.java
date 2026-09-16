package com.athena.github;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

public class SyncResolvedDiscussionsAndViewedFilesSteps {

    private final GitHubTestContext context;
    private final FakeGitHubGraphQLTransport graphQLTransport = new FakeGitHubGraphQLTransport();
    private String repositoryFullName;
    private int pullRequestNumber;
    private boolean succeeded;
    private Exception thrown;

    public SyncResolvedDiscussionsAndViewedFilesSteps(GitHubTestContext context) {
        this.context = context;
    }

    @Given("review thread {string} on pull request {int} in repository {string} can be resolved")
    public void review_thread_can_be_resolved(String threadId, int number, String repo) {
        repositoryFullName = repo;
        pullRequestNumber = number;
        graphQLTransport.allowResolvingThread(threadId);
    }

    @Given("review thread {string} cannot be resolved")
    public void review_thread_cannot_be_resolved(String threadId) {
        // no-op: absence from the allowed set is the fake's default state
    }

    @Given("pull request {int} in repository {string} accepts viewed-file sync for {string}")
    public void pull_request_accepts_viewed_file_sync(int number, String repo, String path) {
        repositoryFullName = repo;
        pullRequestNumber = number;
        graphQLTransport.allowMarkingFileViewed(repo, number, path);
    }

    @When("Athena resolves discussion thread {string} on pull request {int} in repository {string}")
    public void athena_resolves_discussion_thread(String threadId, int number, String repo) {
        DiscussionAndViewedFileSyncer syncer = new DiscussionAndViewedFileSyncer(context.token, graphQLTransport);
        try {
            syncer.resolveDiscussionThread(threadId);
            succeeded = true;
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("Athena marks file {string} as viewed on pull request {int} in repository {string}")
    public void athena_marks_file_as_viewed(String path, int number, String repo) {
        DiscussionAndViewedFileSyncer syncer = new DiscussionAndViewedFileSyncer(context.token, graphQLTransport);
        try {
            syncer.markFileAsViewed(repo, number, path);
            succeeded = true;
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("Athena resolves discussion thread {string} on pull request {int} in repository {string} twice")
    public void athena_resolves_discussion_thread_twice(String threadId, int number, String repo) {
        DiscussionAndViewedFileSyncer syncer = new DiscussionAndViewedFileSyncer(context.token, graphQLTransport);
        try {
            syncer.resolveDiscussionThread(threadId);
            syncer.resolveDiscussionThread(threadId);
            succeeded = true;
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("Athena marks file {string} as viewed on pull request {int} in repository {string} twice")
    public void athena_marks_file_as_viewed_twice(String path, int number, String repo) {
        DiscussionAndViewedFileSyncer syncer = new DiscussionAndViewedFileSyncer(context.token, graphQLTransport);
        try {
            syncer.markFileAsViewed(repo, number, path);
            syncer.markFileAsViewed(repo, number, path);
            succeeded = true;
        } catch (Exception e) {
            thrown = e;
        }
    }

    @Then("the resolve sync succeeds")
    public void resolve_sync_succeeds() {
        assertThat(succeeded).isTrue();
        assertThat(thrown).isNull();
    }

    @Then("GitHub shows review thread {string} as resolved")
    public void github_shows_thread_resolved(String threadId) {
        assertThat(graphQLTransport.resolvedThreads()).contains(threadId);
    }

    @Then("GitHub received exactly one resolve mutation for review thread {string}")
    public void github_received_exactly_one_resolve_mutation(String threadId) {
        assertThat(graphQLTransport.resolveMutationCallCount(threadId)).isEqualTo(1);
    }

    @Then("the viewed-file sync succeeds")
    public void viewed_file_sync_succeeds() {
        assertThat(succeeded).isTrue();
        assertThat(thrown).isNull();
    }

    @Then("GitHub shows file {string} as viewed on pull request {int}")
    public void github_shows_file_viewed(String path, int number) {
        assertThat(graphQLTransport.viewedFiles(repositoryFullName, number)).contains(path);
    }

    @Then("GitHub received exactly one mark-as-viewed mutation for file {string} on pull request {int}")
    public void github_received_exactly_one_mark_viewed_mutation(String path, int number) {
        assertThat(graphQLTransport.markViewedMutationCallCount(repositoryFullName, number, path)).isEqualTo(1);
    }

    @Then("the resolve sync fails clearly")
    public void resolve_sync_fails_clearly() {
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isNotBlank();
    }

    @Then("the viewed-file sync fails clearly")
    public void viewed_file_sync_fails_clearly() {
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isNotBlank();
    }
}
