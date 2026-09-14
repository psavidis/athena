package com.athena.cli;

import com.athena.git.GitAskpass;
import com.athena.git.GitCheckoutException;
import com.athena.git.TempDirectories;
import com.athena.github.FakeGitHubTransport;
import com.athena.repository.ImportedPullRequest;
import com.athena.github.PullRequestImporter;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PrReviewSummarySteps {

    private static final String PR_TITLE = "Move authentication to Account";
    private static final String REPOSITORY_FULL_NAME = "acme/widgets";

    private Path repoDir;
    private Path workDir;
    private ImportedPullRequest importedPr;
    private String summary;
    private String token;
    private Map<String, String> credentialHelperEnvironment;

    @Before
    public void createTempRoots() throws IOException {
        repoDir = Files.createTempDirectory("athena-cli-repo-");
        workDir = Files.createTempDirectory("athena-cli-work-");
    }

    @After
    public void cleanUpTempRoots() {
        TempDirectories.deleteRecursively(repoDir);
        TempDirectories.deleteRecursively(workDir);
    }

    @Given("a PR whose base and head revisions are real git commits differing by a rename")
    public void a_pr_differing_by_a_rename() throws IOException, InterruptedException {
        initRepo();
        writeFile("Greeter.java", "public class Greeter {\n"
                + "    public String greet() {\n"
                + "        return \"hi\";\n"
                + "    }\n"
                + "}\n");
        String baseSha = commit("Add Greeter");

        writeFile("Greeter.java", "public class Greeter {\n"
                + "    public String salute() {\n"
                + "        return \"hi\";\n"
                + "    }\n"
                + "}\n");
        String headSha = commit("Rename greet to salute");

        importedPr = buildImportedPullRequest(baseSha, headSha);
    }

    @Given("a PR whose base and head revisions are real git commits differing by a mechanical replacement")
    public void a_pr_differing_by_a_mechanical_replacement() throws IOException, InterruptedException {
        initRepo();
        for (int i = 0; i < 3; i++) {
            writeFile("Ref" + i + ".java", "public class Ref" + i + " {\n"
                    + "    public Foo make() {\n"
                    + "        return new Foo();\n"
                    + "    }\n"
                    + "}\n");
        }
        writeFile("Foo.java", "public class Foo {\n}\n");
        String baseSha = commit("Add Foo references");

        for (int i = 0; i < 3; i++) {
            writeFile("Ref" + i + ".java", "public class Ref" + i + " {\n"
                    + "    public Bar make() {\n"
                    + "        return new Bar();\n"
                    + "    }\n"
                    + "}\n");
        }
        Files.delete(repoDir.resolve("Foo.java"));
        writeFile("Bar.java", "public class Bar {\n}\n");
        String headSha = commit("Replace Foo with Bar");

        importedPr = buildImportedPullRequest(baseSha, headSha);
    }

    @Given("a PR whose head revision is only reachable as a dangling commit because its branch was deleted")
    public void a_pr_whose_head_revision_is_a_dangling_commit() throws IOException, InterruptedException {
        initRepo();
        run(repoDir, "git", "checkout", "--quiet", "-b", "trunk");
        writeFile("Greeter.java", "public class Greeter {\n"
                + "    public String greet() {\n"
                + "        return \"hi\";\n"
                + "    }\n"
                + "}\n");
        String baseSha = commit("Add Greeter");

        run(repoDir, "git", "checkout", "--quiet", "-b", "feature");
        writeFile("Greeter.java", "public class Greeter {\n"
                + "    public String salute() {\n"
                + "        return \"hi\";\n"
                + "    }\n"
                + "}\n");
        String headSha = commit("Rename greet to salute");

        // Simulates the real-world case this ticket exists for: a PR merged via squash, its
        // source branch deleted, leaving the head commit unreachable from any branch/tag.
        run(repoDir, "git", "checkout", "--quiet", "trunk");
        run(repoDir, "git", "branch", "-D", "feature");

        importedPr = buildImportedPullRequest(baseSha, headSha);
    }

    @Given("a PR whose head revision does not exist in its repository")
    public void a_pr_whose_head_revision_does_not_exist() throws IOException, InterruptedException {
        initRepo();
        writeFile("Greeter.java", "public class Greeter {\n}\n");
        String baseSha = commit("Add Greeter");
        String noSuchSha = "0000000000000000000000000000000000dead";

        importedPr = buildImportedPullRequest(baseSha, noSuchSha);
    }

    @When("the reviewer runs the PR review summary")
    public void the_reviewer_runs_the_pr_review_summary() {
        summary = PrReviewSummary.buildFor(repoDir.toString(), workDir, Map.of(), importedPr);
    }

    @When("the reviewer runs the PR review summary and it fails")
    public void the_reviewer_runs_the_pr_review_summary_and_it_fails() {
        assertThatThrownBy(() -> PrReviewSummary.buildFor(repoDir.toString(), workDir, Map.of(), importedPr))
                .isInstanceOf(GitCheckoutException.class);
    }

    @Then("the summary includes the PR's title")
    public void the_summary_includes_the_prs_title() {
        assertThat(summary).contains(PR_TITLE);
    }

    @Then("the summary lists the rename Change under category {string}")
    public void the_summary_lists_the_rename_change_under_category(String category) {
        assertThat(summary).contains(category + ":");
        assertThat(summary).contains("Rename");
    }

    @Then("the summary reports the rename Change's occurrence and exception counts")
    public void the_summary_reports_occurrence_and_exception_counts() {
        assertThat(summary).contains("1 occurrences, 0 exceptions");
    }

    @Then("the summary lists the mechanical replacement Change under category {string}")
    public void the_summary_lists_the_mechanical_change_under_category(String category) {
        assertThat(summary).contains(category + ":");
        assertThat(summary).contains("Rename Foo -> Bar");
    }

    @Then("no checkout directories are left behind")
    public void no_checkout_directories_are_left_behind() throws IOException {
        try (var entries = Files.list(workDir)) {
            assertThat(entries).isEmpty();
        }
    }

    @Given("a GitHub Personal Access Token {string}")
    public void a_github_personal_access_token(String tokenValue) {
        token = tokenValue;
    }

    @When("the git credential helper environment is built for that token")
    public void the_git_credential_helper_environment_is_built() {
        credentialHelperEnvironment = GitAskpass.environmentFor(token);
    }

    @Then("invoking the helper script with a Username prompt answers {string}")
    public void invoking_the_helper_script_with_a_username_prompt_answers(String expected) throws IOException, InterruptedException {
        assertThat(runAskpassScript("Username for 'https://github.com': ")).isEqualTo(expected);
    }

    @Then("invoking the helper script with a Password prompt answers the token")
    public void invoking_the_helper_script_with_a_password_prompt_answers_the_token() throws IOException, InterruptedException {
        assertThat(runAskpassScript("Password for 'https://x-access-token@github.com': ")).isEqualTo(token);
    }

    private String runAskpassScript(String prompt) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(credentialHelperEnvironment.get("GIT_ASKPASS"), prompt);
        builder.environment().putAll(credentialHelperEnvironment);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor();
        return output.strip();
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

    private ImportedPullRequest buildImportedPullRequest(String baseSha, String headSha) {
        FakeGitHubTransport transport = new FakeGitHubTransport();
        transport.acceptToken("test-token", "reviewer");
        transport.addPullRequestDetail(REPOSITORY_FULL_NAME, 1, PR_TITLE, "author", baseSha, headSha);
        return new PullRequestImporter("test-token", transport).importPullRequest(REPOSITORY_FULL_NAME, 1);
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
