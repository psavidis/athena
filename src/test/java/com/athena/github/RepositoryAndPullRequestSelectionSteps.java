package com.athena.github;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RepositoryAndPullRequestSelectionSteps {

    // Fake GitHub HTTP boundary: real network calls to api.github.com are an
    // external system boundary the test can't/shouldn't cross for real.
    private final FakeGitHubTransport transport = new FakeGitHubTransport();

    private String token;
    private GitHubRepositoryBrowser browser;
    private List<Repository> repositories;
    private List<PullRequestSummary> pullRequests;
    private PullRequestSummary selectedPullRequest;
    private Exception thrown;

    @Given("a valid GitHub Personal Access Token")
    public void a_valid_token() {
        token = "valid-token";
        transport.acceptToken(token, "octocat");
    }

    @Given("an invalid GitHub Personal Access Token")
    public void an_invalid_token() {
        token = "invalid-token";
        transport.rejectToken(token);
    }

    @Given("the account has access to repositories {string} and {string}")
    public void account_has_access_to_repositories(String repo1, String repo2) {
        transport.addAccessibleRepository(token, repo1);
        transport.addAccessibleRepository(token, repo2);
    }

    @Given("repository {string} has open pull request {int} {string}")
    public void repository_has_open_pull_request(String repo, int number, String title) {
        transport.addOpenPullRequest(repo, number, title);
    }

    @Given("repository {string} has no pull request {int}")
    public void repository_has_no_pull_request(String repo, int number) {
        // no-op: absence is the default state of the fake transport
    }

    @When("Athena lists the accessible repositories")
    public void athena_lists_repositories() {
        browser = new GitHubRepositoryBrowser(token, transport);
        try {
            repositories = browser.listAccessibleRepositories();
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("Athena lists open pull requests for repository {string}")
    public void athena_lists_pull_requests(String repo) {
        browser = new GitHubRepositoryBrowser(token, transport);
        try {
            pullRequests = browser.listOpenPullRequests(repo);
        } catch (Exception e) {
            thrown = e;
        }
    }

    @When("Athena selects pull request {int} in repository {string}")
    public void athena_selects_pull_request(int number, String repo) {
        browser = new GitHubRepositoryBrowser(token, transport);
        try {
            selectedPullRequest = browser.selectPullRequest(repo, number);
        } catch (Exception e) {
            thrown = e;
        }
    }

    @Then("the repository list includes {string} and {string}")
    public void repository_list_includes(String repo1, String repo2) {
        assertThat(repositories).extracting(Repository::fullName)
                .contains(repo1, repo2);
    }

    @Then("the pull request list includes pull request {int} {string}")
    public void pull_request_list_includes(int number, String title) {
        assertThat(pullRequests)
                .anySatisfy(pr -> {
                    assertThat(pr.number()).isEqualTo(number);
                    assertThat(pr.title()).isEqualTo(title);
                });
    }

    @Then("the selected pull request is {int} {string}")
    public void selected_pull_request_is(int number, String title) {
        assertThat(selectedPullRequest.number()).isEqualTo(number);
        assertThat(selectedPullRequest.title()).isEqualTo(title);
    }

    @Then("the selection fails clearly")
    public void selection_fails_clearly() {
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isNotBlank();
    }

    @Then("the listing fails clearly")
    public void listing_fails_clearly() {
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isNotBlank();
    }
}
