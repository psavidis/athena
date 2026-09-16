package com.athena.contextrewind;

import com.athena.git.TempDirectories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit tests for {@link EntityGitHistoryReader} (ticket #161) —
 * a single file's own dated commit history, oldest first, with no
 * exception when the path isn't a git repository or the file has no
 * history there.
 */
class EntityGitHistoryReaderTest {

    private Path projectRoot;
    private final List<String> shasInCommitOrder = new ArrayList<>();

    @BeforeEach
    void createProject() throws IOException {
        projectRoot = Files.createTempDirectory("athena-entity-git-history-reader-test-");
    }

    @AfterEach
    void cleanUp() {
        TempDirectories.deleteRecursively(projectRoot);
    }

    @Test
    void listsCommitsThatTouchedTheFileOldestFirst() throws IOException, InterruptedException {
        initRepo();
        commitFile("PaymentProcessor.java", "add processor", "2025-12-01T10:00:00+00:00");
        commitFile("PaymentProcessor.java", "add retry handling", "2026-02-01T10:00:00+00:00");

        List<HistoricalActivity> activity = EntityGitHistoryReader.read(projectRoot, "PaymentProcessor.java");

        assertThat(activity).extracting(HistoricalActivity::description)
                .containsExactly(
                        "add processor (" + shasInCommitOrder.get(0) + ")",
                        "add retry handling (" + shasInCommitOrder.get(1) + ")");
        assertThat(activity).extracting(HistoricalActivity::occurredAt)
                .containsExactly(Instant.parse("2025-12-01T10:00:00Z"), Instant.parse("2026-02-01T10:00:00Z"));
    }

    @Test
    void findsCommitsForAFileUnderANestedPackageDirectory() throws IOException, InterruptedException {
        initRepo();
        commitFile("src/main/java/com/acme/PaymentProcessor.java", "add processor", "2025-12-01T10:00:00+00:00");

        List<HistoricalActivity> activity = EntityGitHistoryReader.read(projectRoot, "PaymentProcessor.java");

        assertThat(activity).hasSize(1);
        assertThat(activity.get(0).description()).contains("add processor");
    }

    @Test
    void doesNotIncludeCommitsThatTouchedAnotherFile() throws IOException, InterruptedException {
        initRepo();
        commitFile("Unrelated.java", "unrelated change", "2025-12-01T10:00:00+00:00");

        List<HistoricalActivity> activity = EntityGitHistoryReader.read(projectRoot, "PaymentProcessor.java");

        assertThat(activity).isEmpty();
    }

    @Test
    void returnsEmptyWithoutThrowingWhenTheProjectRootIsNotAGitRepository() {
        List<HistoricalActivity> activity = EntityGitHistoryReader.read(projectRoot, "PaymentProcessor.java");

        assertThat(activity).isEmpty();
    }

    private void initRepo() throws IOException, InterruptedException {
        runGit("init", "--quiet");
        runGit("config", "user.email", "test@example.com");
        runGit("config", "user.name", "Test");
    }

    private void commitFile(String fileName, String message, String isoCommitDate) throws IOException, InterruptedException {
        Path file = projectRoot.resolve(fileName);
        Files.createDirectories(file.getParent());
        String previousContent = Files.exists(file) ? Files.readString(file) : "";
        Files.writeString(file, previousContent + message + "\n");
        runGit("add", ".");

        ProcessBuilder builder = new ProcessBuilder("git", "commit", "--quiet", "-m", message)
                .directory(projectRoot.toFile());
        builder.environment().put("GIT_AUTHOR_DATE", isoCommitDate);
        builder.environment().put("GIT_COMMITTER_DATE", isoCommitDate);
        Process process = builder.start();
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git commit failed: " + new String(process.getErrorStream().readAllBytes()));
        }
        shasInCommitOrder.add(shortHeadSha());
    }

    private String shortHeadSha() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .directory(projectRoot.toFile()).start();
        String output = new String(process.getInputStream().readAllBytes()).strip();
        process.waitFor();
        return output;
    }

    private void runGit(String... args) throws IOException, InterruptedException {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).directory(projectRoot.toFile()).start();
        if (process.waitFor() != 0) {
            throw new IllegalStateException("Command failed: " + String.join(" ", command));
        }
    }
}
