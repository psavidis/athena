package com.athena.github;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

public class SyncCommentsToGitHubSteps {

    private final GitHubTestContext context;
    private String repositoryFullName;
    private int pullRequestNumber;
    private boolean syncSucceeded;
    private Exception thrown;

    public SyncCommentsToGitHubSteps(GitHubTestContext context) {
        this.context = context;
    }

    @Given("repository {string} pull request {int} accepts synced comments")
    public void repository_accepts_synced_comments(String repo, int number) {
        repositoryFullName = repo;
        pullRequestNumber = number;
        context.transport.enableCommentSync(repo, number);
    }

    @Given("repository {string} pull request {int} is at commit {string} and accepts synced comments")
    public void repository_at_commit_accepts_synced_comments(String repo, int number, String sha) {
        repositoryFullName = repo;
        pullRequestNumber = number;
        context.transport.enableCommentSync(repo, number);
        context.transport.addPullRequestDetail(repo, number, "Fix typo", "octocat", "base123", sha);
    }

    @When("Athena syncs a general comment {string} to pull request {int} in repository {string}")
    public void athena_syncs_general_comment(String body, int number, String repo) {
        CommentSyncer syncer = new CommentSyncer(context.token, context.transport);
        try {
            syncer.syncGeneralComment(repo, number, body);
            syncSucceeded = true;
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("Athena syncs a line comment {string} on file {string} line {int} to pull request {int} in repository {string}")
    public void athena_syncs_line_comment(String body, String path, int line, int number, String repo) {
        CommentSyncer syncer = new CommentSyncer(context.token, context.transport);
        try {
            syncer.syncLineComment(repo, number, body, path, line);
            syncSucceeded = true;
        } catch (Exception e) {
            thrown = e;
        }
    }

    @Then("the sync succeeds")
    public void sync_succeeds() {
        assertThat(syncSucceeded).isTrue();
        assertThat(thrown).isNull();
    }

    @Then("GitHub shows a general comment {string} on pull request {int}")
    public void github_shows_general_comment(String body, int number) {
        assertThat(context.transport.postedGeneralComments(repositoryFullName, number)).contains(body);
    }

    @Then("GitHub shows a line comment {string} on file {string} line {int} of pull request {int}")
    public void github_shows_line_comment(String body, String path, int line, int number) {
        assertThat(context.transport.postedLineComments(repositoryFullName, number))
                .anySatisfy(c -> {
                    assertThat(c.body()).isEqualTo(body);
                    assertThat(c.path()).isEqualTo(path);
                    assertThat(c.line()).isEqualTo(line);
                });
    }

    @Then("the sync fails clearly")
    public void sync_fails_clearly() {
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isNotBlank();
    }
}
