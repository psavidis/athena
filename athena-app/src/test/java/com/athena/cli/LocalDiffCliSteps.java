package com.athena.cli;

import com.athena.git.GitCheckoutException;
import com.athena.git.TempDirectories;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Steps for {@code runnable_cli_local_diff_summary.feature} (ticket #111/#154):
 * {@link PrReviewSummary}'s new local-Diff entry point, exercised the same
 * way {@link PrReviewSummarySteps} exercises the existing PR-mode one — a
 * real local git repository, no fakes/mocks, since checking out real
 * revisions is exactly the behavior under test.
 */
public class LocalDiffCliSteps {

    private Path repoDir;
    private Path workDir;
    private String baseSha;
    private String headSha;
    private String summary;

    @Before
    public void createTempRoots() throws IOException {
        repoDir = Files.createTempDirectory("athena-cli-local-diff-repo-");
        workDir = Files.createTempDirectory("athena-cli-local-diff-work-");
    }

    @After
    public void cleanUpTempRoots() {
        TempDirectories.deleteRecursively(repoDir);
        TempDirectories.deleteRecursively(workDir);
    }

    @Given("a local repository whose base and head revisions are real git commits differing by a rename")
    public void a_local_repository_differing_by_a_rename() throws IOException, InterruptedException {
        initRepo();
        writeFile("Greeter.java", "public class Greeter {\n"
                + "    public String greet() {\n"
                + "        return \"hi\";\n"
                + "    }\n"
                + "}\n");
        baseSha = commit("Add Greeter");

        writeFile("Greeter.java", "public class Greeter {\n"
                + "    public String salute() {\n"
                + "        return \"hi\";\n"
                + "    }\n"
                + "}\n");
        headSha = commit("Rename greet to salute");
    }

    @Given("a local repository whose base and head revisions are real git commits differing by a mechanical replacement")
    public void a_local_repository_differing_by_a_mechanical_replacement() throws IOException, InterruptedException {
        initRepo();
        for (int i = 0; i < 3; i++) {
            writeFile("Ref" + i + ".java", "public class Ref" + i + " {\n"
                    + "    public Foo make() {\n"
                    + "        return new Foo();\n"
                    + "    }\n"
                    + "}\n");
        }
        writeFile("Foo.java", "public class Foo {\n}\n");
        baseSha = commit("Add Foo references");

        for (int i = 0; i < 3; i++) {
            writeFile("Ref" + i + ".java", "public class Ref" + i + " {\n"
                    + "    public Bar make() {\n"
                    + "        return new Bar();\n"
                    + "    }\n"
                    + "}\n");
        }
        Files.delete(repoDir.resolve("Foo.java"));
        writeFile("Bar.java", "public class Bar {\n}\n");
        headSha = commit("Replace Foo with Bar");
    }

    @Given("a local repository whose head revision does not exist")
    public void a_local_repository_whose_head_revision_does_not_exist() throws IOException, InterruptedException {
        initRepo();
        writeFile("Greeter.java", "public class Greeter {\n}\n");
        baseSha = commit("Add Greeter");
        headSha = "0000000000000000000000000000000000dead";
    }

    @When("the reviewer runs the local Diff summary")
    public void the_reviewer_runs_the_local_diff_summary() {
        summary = PrReviewSummary.buildForLocalDiff(repoDir.toString(), workDir, baseSha, headSha);
    }

    @When("the reviewer runs the local Diff summary and it fails")
    public void the_reviewer_runs_the_local_diff_summary_and_it_fails() {
        assertThatThrownBy(() -> PrReviewSummary.buildForLocalDiff(repoDir.toString(), workDir, baseSha, headSha))
                .isInstanceOf(GitCheckoutException.class);
    }

    @Then("the local Diff summary lists the rename Change under category {string}")
    public void the_local_diff_summary_lists_the_rename_change_under_category(String category) {
        assertThat(summary).contains(category + ":");
        assertThat(summary).contains("Rename");
    }

    @Then("the local Diff summary reports the rename Change's occurrence and exception counts")
    public void the_local_diff_summary_reports_occurrence_and_exception_counts() {
        assertThat(summary).contains("1 occurrences, 0 exceptions");
    }

    @Then("the local Diff summary lists the mechanical replacement Change under category {string}")
    public void the_local_diff_summary_lists_the_mechanical_change_under_category(String category) {
        assertThat(summary).contains(category + ":");
        assertThat(summary).contains("Rename Foo -> Bar");
    }

    @Then("no local Diff checkout directories are left behind")
    public void no_local_diff_checkout_directories_are_left_behind() throws IOException {
        try (var entries = Files.list(workDir)) {
            assertThat(entries).isEmpty();
        }
    }

    private void initRepo() throws IOException, InterruptedException {
        run(repoDir, "git", "init", "--quiet");
        run(repoDir, "git", "config", "user.email", "test@example.com");
        run(repoDir, "git", "config", "user.name", "Test");
    }

    private void writeFile(String name, String content) throws IOException {
        Files.writeString(repoDir.resolve(name), content);
    }

    private String commit(String message) throws IOException, InterruptedException {
        run(repoDir, "git", "add", ".");
        run(repoDir, "git", "commit", "--quiet", "-m", message);
        return runAndCapture(repoDir, "git", "rev-parse", "HEAD").strip();
    }

    private void run(Path dir, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(dir.toFile()).start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String stderr = new String(process.getErrorStream().readAllBytes());
            throw new IllegalStateException("Command failed: " + String.join(" ", command) + "\n" + stderr);
        }
    }

    private String runAndCapture(Path dir, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(dir.toFile()).start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Command failed: " + String.join(" ", command));
        }
        return output;
    }
}
