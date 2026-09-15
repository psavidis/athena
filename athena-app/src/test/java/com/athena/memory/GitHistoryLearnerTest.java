package com.athena.memory;

import com.athena.git.TempDirectories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Dedicated unit tests for {@link GitHistoryLearner} (ticket #170) — detecting
 * historically co-changed files from a project's real git commit history and
 * recording them through {@link ProjectMemoryStore}.
 */
class GitHistoryLearnerTest {

    private Path repoRoot;
    private ProjectMemoryStore store;

    @BeforeEach
    void createRepo() throws IOException, InterruptedException {
        repoRoot = Files.createTempDirectory("athena-git-history-learner-test-");
        store = new ProjectMemoryStore(repoRoot);
        runGit(repoRoot, "init", "--quiet");
        runGit(repoRoot, "config", "user.email", "test@example.com");
        runGit(repoRoot, "config", "user.name", "Test");
    }

    @AfterEach
    void cleanUpRepo() {
        TempDirectories.deleteRecursively(repoRoot);
    }

    @Test
    void recordsAFilePairThatRepeatedlyChangesTogether() throws IOException, InterruptedException {
        commitFiles("commit 1", "OrderService.java", "OrderProjection.java");
        commitFiles("commit 2", "OrderService.java", "OrderProjection.java");
        commitFiles("commit 3", "OrderService.java", "OrderProjection.java");

        GitHistoryLearner.learn(repoRoot, store);

        MemoryEntry entry = store.entries().stream()
                .filter(e -> e.fact().contains("OrderService.java") && e.fact().contains("OrderProjection.java"))
                .findFirst()
                .orElseThrow();
        assertThat(entry.evidence()).isEqualTo("3 commits");
        assertThat(entry.developerConfirmed()).isFalse();
    }

    @Test
    void doesNotRecordAPairThatOnlyCoOccurredOnce() throws IOException, InterruptedException {
        commitFiles("commit 1", "ReadmeTypo.java", "ChangeLog.java");

        GitHistoryLearner.learn(repoRoot, store);

        assertThat(store.entries()).isEmpty();
    }

    @Test
    void producesNoMemoryForARepositoryWithNoCommitsYet() {
        assertThatCode(() -> GitHistoryLearner.learn(repoRoot, store)).doesNotThrowAnyException();

        assertThat(store.entries()).isEmpty();
    }

    @Test
    void recordsMultipleIndependentPairsSeparately() throws IOException, InterruptedException {
        commitFiles("order 1", "Order.java", "OrderProjection.java");
        commitFiles("order 2", "Order.java", "OrderProjection.java");
        commitFiles("invoice 1", "Invoice.java", "InvoiceView.java");
        commitFiles("invoice 2", "Invoice.java", "InvoiceView.java");

        GitHistoryLearner.learn(repoRoot, store);

        assertThat(store.entries()).hasSize(2);
        assertThat(store.entries()).anyMatch(e -> e.fact().contains("Order.java") && e.fact().contains("OrderProjection.java"));
        assertThat(store.entries()).anyMatch(e -> e.fact().contains("Invoice.java") && e.fact().contains("InvoiceView.java"));
    }

    @Test
    void doesNotDuplicateAPairAlreadyLearnedOnAnEarlierRun() throws IOException, InterruptedException {
        commitFiles("commit 1", "OrderService.java", "OrderProjection.java");
        commitFiles("commit 2", "OrderService.java", "OrderProjection.java");
        GitHistoryLearner.learn(repoRoot, store);

        GitHistoryLearner.learn(repoRoot, store);

        assertThat(store.entries())
                .filteredOn(e -> e.fact().contains("OrderService.java") && e.fact().contains("OrderProjection.java"))
                .hasSize(1);
    }

    @Test
    void recordsACursorAfterLearningSoALaterPassCanSkipAlreadyProcessedCommits() throws IOException, InterruptedException {
        commitFiles("commit 1", "OrderService.java", "OrderProjection.java");

        GitHistoryLearner.learn(repoRoot, store);

        LearningProgressStore progress = new LearningProgressStore(repoRoot);
        assertThat(progress.cursor("git-history")).isPresent();
    }

    @Test
    void accumulatesCoOccurrencesAcrossIncrementalPassesUntilTheThresholdIsCrossed() throws IOException, InterruptedException {
        commitFiles("commit 1", "OrderService.java", "OrderProjection.java");
        GitHistoryLearner.learn(repoRoot, store);
        assertThat(store.entries()).isEmpty();

        commitFiles("commit 2", "OrderService.java", "OrderProjection.java");
        GitHistoryLearner.learn(repoRoot, store);

        MemoryEntry entry = store.entries().stream()
                .filter(e -> e.fact().contains("OrderService.java") && e.fact().contains("OrderProjection.java"))
                .findFirst()
                .orElseThrow();
        assertThat(entry.evidence()).isEqualTo("2 commits");
    }

    @Test
    void learningInTwoIncrementalPassesMatchesASingleFullPassOverTheSameEvidence() throws IOException, InterruptedException {
        commitFiles("commit 1", "OrderService.java", "OrderProjection.java");
        commitFiles("commit 2", "OrderService.java", "OrderProjection.java");
        commitFiles("commit 3", "OrderService.java", "OrderProjection.java");
        GitHistoryLearner.learn(repoRoot, store);

        Path incrementalRepoRoot = Files.createTempDirectory("athena-git-history-learner-test-incremental-");
        try {
            ProjectMemoryStore incrementalStore = new ProjectMemoryStore(incrementalRepoRoot);
            runGit(incrementalRepoRoot, "init", "--quiet");
            runGit(incrementalRepoRoot, "config", "user.email", "test@example.com");
            runGit(incrementalRepoRoot, "config", "user.name", "Test");
            commitFilesIn(incrementalRepoRoot, "commit 1", "OrderService.java", "OrderProjection.java");
            GitHistoryLearner.learn(incrementalRepoRoot, incrementalStore);
            commitFilesIn(incrementalRepoRoot, "commit 2", "OrderService.java", "OrderProjection.java");
            commitFilesIn(incrementalRepoRoot, "commit 3", "OrderService.java", "OrderProjection.java");
            GitHistoryLearner.learn(incrementalRepoRoot, incrementalStore);

            assertThat(incrementalStore.entries()).isEqualTo(store.entries());
        } finally {
            TempDirectories.deleteRecursively(incrementalRepoRoot);
        }
    }

    private void commitFiles(String message, String... fileNames) throws IOException, InterruptedException {
        commitFilesIn(repoRoot, message, fileNames);
    }

    private void commitFilesIn(Path root, String message, String... fileNames) throws IOException, InterruptedException {
        for (String fileName : fileNames) {
            Path file = root.resolve(fileName);
            String previousContent = Files.exists(file) ? Files.readString(file) : "";
            Files.writeString(file, previousContent + message + "\n");
        }
        runGit(root, "add", ".");
        runGit(root, "commit", "--quiet", "-m", message);
    }

    private void runGit(Path dir, String... args) throws IOException, InterruptedException {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).directory(dir.toFile()).start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String stderr = new String(process.getErrorStream().readAllBytes());
            throw new IllegalStateException("Command failed: " + String.join(" ", command) + "\n" + stderr);
        }
    }
}
