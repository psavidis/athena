package com.athena.memory;

import com.athena.github.FakeGitHubTransport;
import com.athena.github.GitHubRepositoryProvider;
import com.athena.git.TempDirectories;
import com.athena.repository.RepositoryProvider;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Steps for the Pull Request History Learning feature (ticket #171). */
public class PullRequestHistoryLearningSteps {

    private static final String TOKEN = "test-token";
    private static final String REPOSITORY = "acme/widgets";

    private final FakeGitHubTransport transport = new FakeGitHubTransport();
    private RepositoryProvider provider;
    private Path projectRoot;
    private ProjectMemoryStore store;
    private RuntimeException failure;
    private int nextPullRequestNumber = 1000;

    @Before
    public void createProject() throws IOException {
        transport.acceptToken(TOKEN, "octocat");
        provider = new GitHubRepositoryProvider(TOKEN, transport);
        projectRoot = Files.createTempDirectory("athena-pr-history-learning-steps-");
        store = new ProjectMemoryStore(projectRoot);
    }

    @After
    public void cleanUp() {
        if (projectRoot != null) {
            TempDirectories.deleteRecursively(projectRoot);
        }
    }

    @Given("a project whose merged Pull Requests include {string} and {string} touched together in {int} Pull Request(s)")
    public void a_project_whose_merged_pull_requests_include_files_touched_together(String fileA, String fileB, int pullRequestCount) {
        for (int i = 0; i < pullRequestCount; i++) {
            int number = nextPullRequestNumber++;
            transport.addClosedPullRequest(REPOSITORY, number, "PR " + number, true);
            registerContent(number, fileA, fileB);
        }
    }

    @Given("a project whose Pull Request {int} touched {string} and {string} and was closed without merging")
    public void a_project_whose_pull_request_touched_files_and_was_closed_without_merging(int number, String fileA, String fileB) {
        transport.addClosedPullRequest(REPOSITORY, number, "PR " + number, false);
        registerContent(number, fileA, fileB);
    }

    @Given("a project whose open Pull Request {int} touches {string} and {string}")
    public void a_project_whose_open_pull_request_touches_files(int number, String fileA, String fileB) {
        transport.addOpenPullRequest(REPOSITORY, number, "PR " + number);
        registerContent(number, fileA, fileB);
    }

    @Given("a project with no closed Pull Requests")
    public void a_project_with_no_closed_pull_requests() {
        // No fixtures registered: an empty transport already answers with no closed/open Pull Requests.
    }

    @Given("Athena has already learned from that project's Pull Requests once")
    public void athena_has_already_learned_from_that_projects_pull_requests_once() {
        PullRequestHistoryLearner.learn(projectRoot, REPOSITORY, provider, store);
    }

    @Given("that project's merged Pull Requests separately gain one more Pull Request touching {string} and {string} together")
    public void that_projects_merged_pull_requests_separately_gain_one_more_pull_request_touching_files_together(String fileA, String fileB) {
        int number = nextPullRequestNumber++;
        transport.addClosedPullRequest(REPOSITORY, number, "PR " + number, true);
        registerContent(number, fileA, fileB);
    }

    @When("Athena learns from that project's Pull Requests")
    public void athena_learns_from_that_projects_pull_requests() {
        try {
            PullRequestHistoryLearner.learn(projectRoot, REPOSITORY, provider, store);
        } catch (RuntimeException e) {
            failure = e;
        }
    }

    @When("Athena learns from that project's Pull Requests again")
    public void athena_learns_from_that_projects_pull_requests_again() {
        athena_learns_from_that_projects_pull_requests();
    }

    @Then("querying that project's memory returns a fact that {string} and {string} were touched together across merged Pull Requests")
    public void querying_that_projects_memory_returns_a_fact_that_files_were_touched_together_across_merged_pull_requests(String fileA, String fileB) {
        assertThat(store.entries())
                .anyMatch(entry -> entry.fact().contains(fileA) && entry.fact().contains(fileB)
                        && entry.fact().contains("across merged Pull Requests"));
    }

    @Then("querying that project's memory does not return a fact that {string} and {string} were touched together across merged Pull Requests")
    public void querying_that_projects_memory_does_not_return_a_fact_that_files_were_touched_together_across_merged_pull_requests(String fileA, String fileB) {
        assertThat(store.entries())
                .noneMatch(entry -> entry.fact().contains(fileA) && entry.fact().contains(fileB)
                        && entry.fact().contains("across merged Pull Requests"));
    }

    @Then("that fact's evidence names {int} Pull Requests")
    public void that_facts_evidence_names_pull_requests(int pullRequestCount) {
        assertThat(store.entries()).extracting(MemoryEntry::evidence).contains(pullRequestCount + " Pull Requests");
    }

    @Then("that fact is reported as an inferred pattern from Pull Request history, not a developer-confirmed fact")
    public void that_fact_is_reported_as_an_inferred_pattern_from_pull_request_history_not_a_developer_confirmed_fact() {
        assertThat(store.entries()).extracting(MemoryEntry::developerConfirmed).contains(false);
    }

    @Then("querying that project's memory returns a fact that {string} and {string} were proposed together in Pull Request {int}, which was closed without merging")
    public void querying_that_projects_memory_returns_a_fact_that_files_were_proposed_together_and_closed_without_merging(String fileA, String fileB, int number) {
        assertThat(store.entries())
                .anyMatch(entry -> entry.fact().contains(fileA) && entry.fact().contains(fileB)
                        && entry.fact().contains(String.valueOf(number))
                        && entry.fact().contains("closed without merging"));
    }

    @Then("querying that project's memory returns a fact that {string} and {string} were touched together in an open Pull Request")
    public void querying_that_projects_memory_returns_a_fact_that_files_were_touched_together_in_an_open_pull_request(String fileA, String fileB) {
        assertThat(store.entries())
                .anyMatch(entry -> entry.fact().contains(fileA) && entry.fact().contains(fileB)
                        && entry.fact().contains("open Pull Request"));
    }

    @Then("that fact's confidence is reported as {string}")
    public void that_facts_confidence_is_reported_as(String confidence) {
        assertThat(store.entries()).extracting(MemoryEntry::confidence).contains(confidence);
    }

    @Then("Athena's Pull Request learning leaves that project's memory empty")
    public void athenas_pull_request_learning_leaves_that_projects_memory_empty() {
        assertThat(store.entries()).isEmpty();
    }

    @Then("Pull Request learning does not fail with an exception")
    public void pull_request_learning_does_not_fail_with_an_exception() {
        assertThat(failure).isNull();
    }

    @Then("querying that project's memory returns exactly one fact that {string} and {string} were touched together across merged Pull Requests")
    public void querying_that_projects_memory_returns_exactly_one_fact_that_files_were_touched_together_across_merged_pull_requests(String fileA, String fileB) {
        assertThat(store.entries())
                .filteredOn(entry -> entry.fact().contains(fileA) && entry.fact().contains(fileB)
                        && entry.fact().contains("across merged Pull Requests"))
                .hasSize(1);
    }

    private void registerContent(int number, String... files) {
        transport.addPullRequestDetail(REPOSITORY, number, "PR " + number, "octocat",
                "base-" + number, "head-" + number);
        for (String file : files) {
            transport.addChangedFile(REPOSITORY, number, file, "modified", null);
        }
    }
}
