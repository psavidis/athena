package com.athena.memory;

import com.athena.git.TempDirectories;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Steps for the Project Memory Foundation feature (ticket #169). */
public class ProjectMemoryFoundationSteps {

    private Path projectRoot;
    private Path secondProjectRoot;
    private ProjectMemoryStore store;
    private ProjectMemoryStore secondStore;
    private List<MemoryEntry> result;
    private RuntimeException failure;

    @After
    public void cleanUp() {
        if (projectRoot != null) {
            TempDirectories.deleteRecursively(projectRoot);
        }
        if (secondProjectRoot != null) {
            TempDirectories.deleteRecursively(secondProjectRoot);
        }
    }

    @Given("a project with no recorded memory")
    public void a_project_with_no_recorded_memory() throws IOException {
        projectRoot = Files.createTempDirectory("athena-project-memory-test-");
        store = new ProjectMemoryStore(projectRoot);
    }

    @Given("a project whose memory already contains the fact {string}")
    public void a_project_whose_memory_already_contains_the_fact(String fact) throws IOException {
        a_project_with_no_recorded_memory();
        store.record(new MemoryEntry(fact, "unspecified", "unspecified", false));
    }

    @Given("a project with recorded memory")
    public void a_project_with_recorded_memory() throws IOException {
        a_project_whose_memory_already_contains_the_fact("a recorded fact");
    }

    @Given("a project whose {string} directory contains recorded memory")
    public void a_project_whose_directory_contains_recorded_memory(String directoryName) throws IOException {
        assertThat(directoryName).isEqualTo(".athena/memory");
        a_project_with_recorded_memory();
    }

    @Given("two different projects, each with recorded memory")
    public void two_different_projects_each_with_recorded_memory() throws IOException {
        a_project_with_recorded_memory();
        secondProjectRoot = Files.createTempDirectory("athena-project-memory-test-");
        secondStore = new ProjectMemoryStore(secondProjectRoot);
        secondStore.record(new MemoryEntry("a recorded fact", "unspecified", "unspecified", false));
    }

    @When("Athena's project memory is queried for that project")
    public void athenas_project_memory_is_queried_for_that_project() {
        try {
            result = store.entries();
        } catch (RuntimeException e) {
            failure = e;
        }
    }

    @When("Athena records the learned fact {string} for that project, with evidence {string} and confidence {string}")
    public void athena_records_the_learned_fact_with_evidence_and_confidence(String fact, String evidence, String confidence) {
        store.record(new MemoryEntry(fact, evidence, confidence, false));
    }

    @When("Athena records the additional fact {string} for that project")
    public void athena_records_the_additional_fact_for_that_project(String fact) {
        store.record(new MemoryEntry(fact, "unspecified", "unspecified", false));
    }

    @When("Athena records the inferred pattern {string} for that project, without developer confirmation")
    public void athena_records_the_inferred_pattern_for_that_project_without_developer_confirmation(String fact) {
        store.record(new MemoryEntry(fact, "unspecified", "unspecified", false));
    }

    @When("Athena records the fact {string} for that project, as confirmed by a developer")
    public void athena_records_the_fact_for_that_project_as_confirmed_by_a_developer(String fact) {
        store.record(new MemoryEntry(fact, "unspecified", "unspecified", true));
    }

    @When("Athena records a fact for the first project")
    public void athena_records_a_fact_for_the_first_project() {
        store.record(new MemoryEntry("a fact only the first project should know", "unspecified", "unspecified", false));
    }

    @When("that directory is deleted directly from disk")
    public void that_directory_is_deleted_directly_from_disk() {
        TempDirectories.deleteRecursively(projectRoot.resolve(".athena").resolve("memory"));
    }

    @Then("the result is empty")
    public void the_result_is_empty() {
        assertThat(result).isEmpty();
    }

    @Then("no exception is thrown")
    public void no_exception_is_thrown() {
        assertThat(failure).isNull();
    }

    @Then("querying that project's memory returns a fact {string}")
    public void querying_that_projects_memory_returns_a_fact(String fact) {
        assertThat(store.entries()).extracting(MemoryEntry::fact).contains(fact);
    }

    @Then("that fact's evidence is {string}")
    public void that_facts_evidence_is(String evidence) {
        assertThat(lastRecordedFact().evidence()).isEqualTo(evidence);
    }

    @Then("that fact's confidence is {string}")
    public void that_facts_confidence_is(String confidence) {
        assertThat(lastRecordedFact().confidence()).isEqualTo(confidence);
    }

    @Then("querying that project's memory returns both facts")
    public void querying_that_projects_memory_returns_both_facts() {
        assertThat(store.entries()).extracting(MemoryEntry::fact)
                .contains("Order and OrderProjection frequently change together")
                .contains("Reporting adapters intentionally bypass the repository boundary");
    }

    @Then("querying that project's memory reports the first as an inferred pattern")
    public void querying_that_projects_memory_reports_the_first_as_an_inferred_pattern() {
        MemoryEntry entry = store.entries().stream()
                .filter(e -> e.fact().equals("these components appear to change together"))
                .findFirst()
                .orElseThrow();
        assertThat(entry.developerConfirmed()).isFalse();
    }

    @Then("querying that project's memory reports the second as a developer-confirmed fact")
    public void querying_that_projects_memory_reports_the_second_as_a_developer_confirmed_fact() {
        MemoryEntry entry = store.entries().stream()
                .filter(e -> e.fact().equals("reporting adapters intentionally bypass the repository boundary"))
                .findFirst()
                .orElseThrow();
        assertThat(entry.developerConfirmed()).isTrue();
    }

    @Then("querying the second project's memory does not return that fact")
    public void querying_the_second_projects_memory_does_not_return_that_fact() {
        assertThat(secondStore.entries()).extracting(MemoryEntry::fact)
                .doesNotContain("a fact only the first project should know");
    }

    @Then("a {string} directory exists under that project's own root, containing the recorded memory")
    public void a_directory_exists_under_that_projects_own_root_containing_the_recorded_memory(String directoryName) throws IOException {
        assertThat(directoryName).isEqualTo(".athena/memory");
        Path memoryDir = projectRoot.resolve(".athena").resolve("memory");
        assertThat(memoryDir).isDirectory();
        try (var contents = Files.list(memoryDir)) {
            assertThat(contents).isNotEmpty();
        }
    }

    @Then("querying that project's memory is empty")
    public void querying_that_projects_memory_is_empty() {
        try {
            result = store.entries();
        } catch (RuntimeException e) {
            failure = e;
        }
        assertThat(result).isEmpty();
    }

    private MemoryEntry lastRecordedFact() {
        List<MemoryEntry> entries = store.entries();
        return entries.get(entries.size() - 1);
    }
}
