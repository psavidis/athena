package com.athena.github;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

public class GitHubAuthenticationSteps {

    private final GitHubTestContext context;
    private GitHubConnectionResult result;

    public GitHubAuthenticationSteps(GitHubTestContext context) {
        this.context = context;
    }

    @Given("a valid GitHub Personal Access Token")
    public void a_valid_token() {
        context.token = "valid-token";
        context.transport.acceptToken(context.token, "octocat");
    }

    @Given("an invalid GitHub Personal Access Token")
    public void an_invalid_token() {
        context.token = "invalid-token";
        context.transport.rejectToken(context.token);
    }

    @When("Athena connects to GitHub with that token")
    public void athena_connects() {
        GitHubClient client = new GitHubClient(context.token, context.transport);
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
