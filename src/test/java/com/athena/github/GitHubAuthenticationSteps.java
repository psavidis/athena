package com.athena.github;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

public class GitHubAuthenticationSteps {

    // Fake GitHub HTTP boundary: a real network call to api.github.com is an
    // external system boundary the test can't/shouldn't cross for real, so a
    // fake transport stands in for it (Detroit-school exception).
    private final FakeGitHubTransport transport = new FakeGitHubTransport();

    private String token;
    private GitHubConnectionResult result;

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

    @When("Athena connects to GitHub with that token")
    public void athena_connects() {
        GitHubClient client = new GitHubClient(token, transport);
        result = client.connect();
    }

    @Then("the connection succeeds")
    public void connection_succeeds() {
        assertThat(result.isSuccess()).isTrue();
    }

    @Then("the connection fails")
    public void connection_fails() {
        assertThat(result.isSuccess()).isFalse();
    }

    @Then("Athena reports the authenticated GitHub account's username")
    public void reports_username() {
        assertThat(result.authenticatedUsername()).isEqualTo("octocat");
    }

    @Then("Athena reports that the token was rejected")
    public void reports_rejected() {
        assertThat(result.errorMessage()).isNotBlank();
    }
}
