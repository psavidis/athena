package com.athena.memory;

import com.athena.git.TempDirectories;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Steps for the Git History Learning feature (ticket #170). */
public class GitHistoryLearningSteps {

    private Path projectRoot;
    private ProjectMemoryStore store;
    private RuntimeException failure;

    @After
    public void cleanUp() {
        if (projectRoot != null) {
            TempDirectories.deleteRecursively(projectRoot);
        }
    }

    @Given("a project whose git history has {string} and {string} modified together in {int} commit(s)")
    public void a_project_whose_git_history_has_files_modified_together(String fileA, String fileB, int commitCount) throws IOException, InterruptedException {
        ensureRepo();
        for (int i = 1; i <= commitCount; i++) {
            commitFiles("commit " + i, fileA, fileB);
        }
    }

    @Given("that project's git history separately has {string} and {string} modified together in {int} commit(s)")
    public void that_projects_git_history_separately_has_files_modified_together(String fileA, String fileB, int commitCount) throws IOException, InterruptedException {
        a_project_whose_git_history_has_files_modified_together(fileA, fileB, commitCount);
    }

    @Given("a project whose git history contains no commits")
    public void a_project_whose_git_history_contains_no_commits() throws IOException, InterruptedException {
        ensureRepo();
    }

    @Given("Athena has already learned from that project's git history once")
    public void athena_has_already_learned_from_that_projects_git_history_once() {
        GitHistoryLearner.learn(projectRoot, store);
    }

    @When("Athena learns from that project's git history")
    public void athena_learns_from_that_projects_git_history() {
        try {
            GitHistoryLearner.learn(projectRoot, store);
        } catch (RuntimeException e) {
            failure = e;
        }
    }

    @When("Athena learns from that project's git history again")
    public void athena_learns_from_that_projects_git_history_again() {
        athena_learns_from_that_projects_git_history();
    }

    @Then("querying that project's memory returns a fact that {string} and {string} change together")
    public void querying_that_projects_memory_returns_a_fact_that_files_change_together(String fileA, String fileB) {
        assertThat(store.entries())
                .anyMatch(entry -> entry.fact().contains(fileA) && entry.fact().contains(fileB));
    }

    @Then("that fact's evidence names {int} commits")
    public void that_facts_evidence_names_commits(int commitCount) {
        assertThat(store.entries()).extracting(MemoryEntry::evidence).contains(commitCount + " commits");
    }

    @Then("that fact is reported as an inferred pattern, not a developer-confirmed fact")
    public void that_fact_is_reported_as_an_inferred_pattern_not_a_developer_confirmed_fact() {
        assertThat(store.entries()).extracting(MemoryEntry::developerConfirmed).contains(false);
    }

    @Then("querying that project's memory does not return a fact about {string} and {string}")
    public void querying_that_projects_memory_does_not_return_a_fact_about(String fileA, String fileB) {
        assertThat(store.entries())
                .noneMatch(entry -> entry.fact().contains(fileA) && entry.fact().contains(fileB));
    }

    @Then("querying that project's memory returns exactly one fact that {string} and {string} change together")
    public void querying_that_projects_memory_returns_exactly_one_fact_that_files_change_together(String fileA, String fileB) {
        assertThat(store.entries())
                .filteredOn(entry -> entry.fact().contains(fileA) && entry.fact().contains(fileB))
                .hasSize(1);
    }

    @Then("Athena's project memory for that project contains no learned facts")
    public void athenas_project_memory_for_that_project_contains_no_learned_facts() {
        assertThat(store.entries()).isEmpty();
    }

    @Then("learning does not fail with an exception")
    public void learning_does_not_fail_with_an_exception() {
        assertThat(failure).isNull();
    }

    private void ensureRepo() throws IOException, InterruptedException {
        if (projectRoot != null) {
            return;
        }
        projectRoot = Files.createTempDirectory("athena-git-history-learning-steps-");
        store = new ProjectMemoryStore(projectRoot);
        runGit("init", "--quiet");
        runGit("config", "user.email", "test@example.com");
        runGit("config", "user.name", "Test");
    }

    private void commitFiles(String message, String... fileNames) throws IOException, InterruptedException {
        for (String fileName : fileNames) {
            Path file = projectRoot.resolve(fileName);
            String previousContent = Files.exists(file) ? Files.readString(file) : "";
            Files.writeString(file, previousContent + message + "\n");
        }
        runGit("add", ".");
        runGit("commit", "--quiet", "-m", message);
    }

    private void runGit(String... args) throws IOException, InterruptedException {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).directory(projectRoot.toFile()).start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String stderr = new String(process.getErrorStream().readAllBytes());
            throw new IllegalStateException("Command failed: " + String.join(" ", command) + "\n" + stderr);
        }
    }
}
