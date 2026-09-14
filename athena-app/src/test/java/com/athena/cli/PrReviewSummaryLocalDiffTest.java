package com.athena.cli;

import com.athena.git.GitCheckoutException;
import com.athena.git.TempDirectories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated unit test for {@link PrReviewSummary#buildForLocalDiff} (ticket #111/#154):
 * the new entry point that needs no {@link com.athena.repository.ImportedPullRequest}/
 * GitHub token at all — {@code runnable_cli_local_diff_summary.feature} already covers
 * this black-box through the same method; this exercises it directly for the cases
 * awkward to phrase as a user-facing scenario (the label used in place of a PR title).
 */
class PrReviewSummaryLocalDiffTest {

    private Path repoDir;
    private Path workDir;

    @BeforeEach
    void createTempRoots() throws IOException {
        repoDir = Files.createTempDirectory("athena-local-diff-summary-repo-");
        workDir = Files.createTempDirectory("athena-local-diff-summary-work-");
    }

    @AfterEach
    void cleanUpTempRoots() {
        TempDirectories.deleteRecursively(repoDir);
        TempDirectories.deleteRecursively(workDir);
    }

    @Test
    void doesNotRequireAPullRequestTitle() throws Exception {
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

        String summary = PrReviewSummary.buildForLocalDiff(repoDir.toString(), workDir, baseSha, headSha);

        assertThat(summary).contains("STRUCTURAL:");
        assertThat(summary).contains("Rename");
    }

    @Test
    void reportsNoChangesForIdenticalRevisions() throws Exception {
        initRepo();
        writeFile("Greeter.java", "public class Greeter {\n}\n");
        String sha = commit("Add Greeter");

        String summary = PrReviewSummary.buildForLocalDiff(repoDir.toString(), workDir, sha, sha);

        assertThat(summary).doesNotContain("STRUCTURAL:").doesNotContain("BEHAVIORAL:")
                .doesNotContain("MECHANICAL:").doesNotContain("UNKNOWN:");
    }

    @Test
    void propagatesACheckoutFailureForAMissingRevision() throws Exception {
        initRepo();
        writeFile("Greeter.java", "public class Greeter {\n}\n");
        String baseSha = commit("Add Greeter");
        String missingSha = "0000000000000000000000000000000000dead";

        assertThatThrownBy(() -> PrReviewSummary.buildForLocalDiff(repoDir.toString(), workDir, baseSha, missingSha))
                .isInstanceOf(GitCheckoutException.class);
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
