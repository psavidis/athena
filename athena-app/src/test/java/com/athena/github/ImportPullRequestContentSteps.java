package com.athena.github;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

public class ImportPullRequestContentSteps {

    private final GitHubTestContext context;
    private String repositoryFullName;
    private int pullRequestNumber;
    private ImportedPullRequest imported;

    public ImportPullRequestContentSteps(GitHubTestContext context) {
        this.context = context;
    }

    @Given("repository {string} has pull request {int} {string} authored by {string} based on revision {string} and headed at revision {string}")
    public void repository_has_pull_request_with_author(String repo, int number, String title, String author,
                                                          String base, String head) {
        repositoryFullName = repo;
        pullRequestNumber = number;
        context.transport.addPullRequestDetail(repo, number, title, author, base, head);
    }

    @Given("repository {string} has pull request {int} {string} based on revision {string} and headed at revision {string}")
    public void repository_has_pull_request(String repo, int number, String title, String base, String head) {
        repositoryFullName = repo;
        pullRequestNumber = number;
        context.transport.addPullRequestDetail(repo, number, title, "octocat", base, head);
    }

    @Given("pull request {int} has commits {string} {string} and {string} {string}")
    public void pull_request_has_commits(int number, String sha1, String message1, String sha2, String message2) {
        context.transport.addCommit(repositoryFullName, number, sha1, message1);
        context.transport.addCommit(repositoryFullName, number, sha2, message2);
    }

    @Given("pull request {int} changed file {string} with status {string}")
    public void pull_request_changed_file_with_status(int number, String path, String status) {
        context.transport.addChangedFile(repositoryFullName, number, path, status, null);
    }

    @Given("pull request {int} changed file {string} with diff {string}")
    public void pull_request_changed_file_with_diff(int number, String path, String diff) {
        context.transport.addChangedFile(repositoryFullName, number, path, "modified", diff.replace("\\n", "\n"));
    }

    @Given("pull request {int} changed file {string} with status {string} and no available diff")
    public void pull_request_changed_file_no_diff(int number, String path, String status) {
        context.transport.addChangedFile(repositoryFullName, number, path, status, null);
    }

    @When("Athena imports pull request {int} from repository {string}")
    public void athena_imports_pull_request(int number, String repo) {
        PullRequestImporter importer = new PullRequestImporter(context.token, context.transport);
        imported = importer.importPullRequest(repo, number);
    }

    @Then("the imported pull request has title {string}")
    public void imported_has_title(String title) {
        assertThat(imported.title()).isEqualTo(title);
    }

    @Then("the imported pull request has author {string}")
    public void imported_has_author(String author) {
        assertThat(imported.author()).isEqualTo(author);
    }

    @Then("the imported pull request has base revision {string}")
    public void imported_has_base_revision(String base) {
        assertThat(imported.baseRevision()).isEqualTo(base);
    }

    @Then("the imported pull request has head revision {string}")
    public void imported_has_head_revision(String head) {
        assertThat(imported.headRevision()).isEqualTo(head);
    }

    @Then("the imported pull request has {int} commits")
    public void imported_has_n_commits(int count) {
        assertThat(imported.commits()).hasSize(count);
    }

    @Then("the imported pull request includes commit {string} {string}")
    public void imported_includes_commit(String sha, String message) {
        assertThat(imported.commits())
                .anySatisfy(c -> {
                    assertThat(c.sha()).isEqualTo(sha);
                    assertThat(c.message()).isEqualTo(message);
                });
    }

    @Then("the imported pull request lists changed file {string} with status {string}")
    public void imported_lists_changed_file(String path, String status) {
        assertThat(imported.changedFiles())
                .anySatisfy(f -> {
                    assertThat(f.path()).isEqualTo(path);
                    assertThat(f.status()).isEqualTo(status);
                });
    }

    @Then("the imported pull request's diff for {string} contains {string}")
    public void imported_diff_contains(String path, String fragment) {
        ChangedFile file = imported.changedFiles().stream()
                .filter(f -> f.path().equals(path))
                .findFirst()
                .orElseThrow();
        assertThat(file.diff()).isPresent();
        assertThat(file.diff().get()).contains(fragment);
    }

    @Then("the imported pull request marks {string} as having no available diff")
    public void imported_marks_no_diff(String path) {
        ChangedFile file = imported.changedFiles().stream()
                .filter(f -> f.path().equals(path))
                .findFirst()
                .orElseThrow();
        assertThat(file.diff()).isEmpty();
    }
}
