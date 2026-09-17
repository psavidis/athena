package com.athena.github;

import com.athena.reviewreplay.TranscriptReference;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for Review Replay's transcript discovery (ticket
 * #213). Detroit-school: real {@link GitHubTranscriptDiscovery} against
 * {@link FakeGitHubTransport} (the existing network-boundary fake) — no
 * mocks of internal collaborators.
 */
public class TranscriptDiscoverySteps {

    private static final String TOKEN = "test-token";
    private static final String REPO = "acme/widgets";
    private static final int PULL_REQUEST = 42;

    private final FakeGitHubTransport transport = new FakeGitHubTransport();
    private final GitHubTranscriptDiscovery discovery = new GitHubTranscriptDiscovery(TOKEN, transport);

    private List<TranscriptReference> discovered;

    @Given("a PR whose description reads {string}")
    public void a_pr_whose_description_reads(String description) {
        setUpPr(description);
    }

    @Given("a PR whose description reads:")
    public void a_pr_whose_description_reads_doc_string(String description) {
        setUpPr(description);
    }

    @Given("a PR whose description is empty")
    public void a_pr_whose_description_is_empty() {
        setUpPr("");
    }

    private void setUpPr(String description) {
        transport.acceptToken(TOKEN, "octocat");
        transport.setPullRequestBody(REPO, PULL_REQUEST, description);
    }

    @When("Athena discovers transcript references for that PR")
    public void athena_discovers_transcript_references_for_that_pr() {
        discovered = discovery.discover(REPO, PULL_REQUEST);
    }

    @Then("a transcript reference for {string} is found")
    public void a_transcript_reference_for_is_found(String platform) {
        assertThat(discovered).anySatisfy(reference -> assertThat(reference.platform()).isEqualTo(platform));
    }

    @Then("its URL is {string}")
    public void its_url_is(String url) {
        assertThat(discovered).anySatisfy(reference -> assertThat(reference.url()).isEqualTo(url));
    }

    @Then("no transcript reference is found")
    public void no_transcript_reference_is_found() {
        assertThat(discovered).isEmpty();
    }

    @Then("{int} transcript references are found")
    public void transcript_references_are_found(int count) {
        assertThat(discovered).hasSize(count);
    }
}
