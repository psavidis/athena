package com.athena.memory;

import com.athena.git.TempDirectories;
import com.athena.github.FakeGitHubTransport;
import com.athena.github.GitHubRepositoryProvider;
import com.athena.repository.RepositoryProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Dedicated unit tests for {@link PullRequestHistoryLearner} (ticket #171) —
 * detecting historically co-changed files across a project's merged Pull
 * Requests, recording a closed-without-merging Pull Request as its own
 * evidence, and treating an open Pull Request's content as provisional,
 * not-yet-established context.
 */
class PullRequestHistoryLearnerTest {

    private static final String TOKEN = "test-token";
    private static final String REPOSITORY = "acme/widgets";

    private final FakeGitHubTransport transport = new FakeGitHubTransport();
    private final RepositoryProvider provider = new GitHubRepositoryProvider(TOKEN, transport);
    private Path projectRoot;
    private ProjectMemoryStore store;

    @BeforeEach
    void createProject() throws IOException {
        transport.acceptToken(TOKEN, "octocat");
        projectRoot = Files.createTempDirectory("athena-pr-history-learner-test-");
        store = new ProjectMemoryStore(projectRoot);
    }

    @Test
    void recordsAFilePairThatRepeatedlyChangesTogetherAcrossMergedPullRequests() {
        addMergedPullRequest(1, "OrderService.java", "OrderProjection.java");
        addMergedPullRequest(2, "OrderService.java", "OrderProjection.java");
        addMergedPullRequest(3, "OrderService.java", "OrderProjection.java");

        PullRequestHistoryLearner.learn(projectRoot, REPOSITORY, provider, store);

        MemoryEntry entry = store.entries().stream()
                .filter(e -> e.fact().contains("OrderService.java") && e.fact().contains("OrderProjection.java"))
                .findFirst()
                .orElseThrow();
        assertThat(entry.evidence()).isEqualTo("3 Pull Requests");
        assertThat(entry.developerConfirmed()).isFalse();
    }

    @Test
    void doesNotRecordAPairThatOnlyCoOccurredInOneMergedPullRequest() {
        addMergedPullRequest(1, "ReadmeTypo.java", "ChangeLog.java");

        PullRequestHistoryLearner.learn(projectRoot, REPOSITORY, provider, store);

        assertThat(store.entries()).isEmpty();
    }

    @Test
    void recordsAClosedWithoutMergingPullRequestAsARejectedChangeNotAnAcceptedPattern() {
        addRejectedPullRequest(42, "LegacyExport.java", "LegacyImport.java");

        PullRequestHistoryLearner.learn(projectRoot, REPOSITORY, provider, store);

        assertThat(store.entries())
                .anyMatch(e -> e.fact().contains("LegacyExport.java")
                        && e.fact().contains("LegacyImport.java")
                        && e.fact().contains("42")
                        && e.fact().contains("closed without merging"));
        assertThat(store.entries())
                .noneMatch(e -> e.fact().contains("LegacyExport.java")
                        && e.fact().contains("LegacyImport.java")
                        && e.fact().contains("across merged Pull Requests"));
    }

    @Test
    void recordsOpenPullRequestFilesWithProvisionalConfidence() {
        addOpenPullRequest(55, "Checkout.java", "Payment.java");

        PullRequestHistoryLearner.learn(projectRoot, REPOSITORY, provider, store);

        MemoryEntry entry = store.entries().stream()
                .filter(e -> e.fact().contains("Checkout.java") && e.fact().contains("Payment.java"))
                .findFirst()
                .orElseThrow();
        assertThat(entry.confidence()).isEqualTo("provisional");
    }

    @Test
    void producesNoMemoryWhenThereAreNoPullRequestsYet() {
        assertThatCode(() -> PullRequestHistoryLearner.learn(projectRoot, REPOSITORY, provider, store))
                .doesNotThrowAnyException();

        assertThat(store.entries()).isEmpty();
    }

    @Test
    void doesNotDuplicateAPairAlreadyLearnedOnAnEarlierRun() {
        addMergedPullRequest(1, "OrderService.java", "OrderProjection.java");
        addMergedPullRequest(2, "OrderService.java", "OrderProjection.java");
        PullRequestHistoryLearner.learn(projectRoot, REPOSITORY, provider, store);

        PullRequestHistoryLearner.learn(projectRoot, REPOSITORY, provider, store);

        assertThat(store.entries())
                .filteredOn(e -> e.fact().contains("OrderService.java") && e.fact().contains("OrderProjection.java"))
                .hasSize(1);
    }

    @Test
    void recordsAProcessedMarkerAfterLearningSoALaterPassCanSkipAlreadyProcessedPullRequests() throws IOException {
        addMergedPullRequest(1, "OrderService.java", "OrderProjection.java");

        PullRequestHistoryLearner.learn(projectRoot, REPOSITORY, provider, store);

        LearningProgressStore progress = new LearningProgressStore(projectRoot);
        assertThat(progress.processedMarkers("pr-history")).contains("1");
    }

    @Test
    void accumulatesCoOccurrencesAcrossIncrementalPassesUntilTheThresholdIsCrossed() {
        addMergedPullRequest(1, "OrderService.java", "OrderProjection.java");
        PullRequestHistoryLearner.learn(projectRoot, REPOSITORY, provider, store);
        assertThat(store.entries()).isEmpty();

        addMergedPullRequest(2, "OrderService.java", "OrderProjection.java");
        PullRequestHistoryLearner.learn(projectRoot, REPOSITORY, provider, store);

        MemoryEntry entry = store.entries().stream()
                .filter(e -> e.fact().contains("OrderService.java") && e.fact().contains("OrderProjection.java"))
                .findFirst()
                .orElseThrow();
        assertThat(entry.evidence()).isEqualTo("2 Pull Requests");
    }

    @Test
    void learningInTwoIncrementalPassesMatchesASingleFullPassOverTheSameEvidence() throws IOException {
        addMergedPullRequest(1, "OrderService.java", "OrderProjection.java");
        addMergedPullRequest(2, "OrderService.java", "OrderProjection.java");
        addMergedPullRequest(3, "OrderService.java", "OrderProjection.java");
        PullRequestHistoryLearner.learn(projectRoot, REPOSITORY, provider, store);

        FakeGitHubTransport incrementalTransport = new FakeGitHubTransport();
        incrementalTransport.acceptToken(TOKEN, "octocat");
        RepositoryProvider incrementalProvider = new GitHubRepositoryProvider(TOKEN, incrementalTransport);
        Path incrementalProjectRoot = Files.createTempDirectory("athena-pr-history-learner-test-incremental-");
        try {
            ProjectMemoryStore incrementalStore = new ProjectMemoryStore(incrementalProjectRoot);
            addMergedPullRequest(incrementalTransport, 1, "OrderService.java", "OrderProjection.java");
            PullRequestHistoryLearner.learn(incrementalProjectRoot, REPOSITORY, incrementalProvider, incrementalStore);
            addMergedPullRequest(incrementalTransport, 2, "OrderService.java", "OrderProjection.java");
            addMergedPullRequest(incrementalTransport, 3, "OrderService.java", "OrderProjection.java");
            PullRequestHistoryLearner.learn(incrementalProjectRoot, REPOSITORY, incrementalProvider, incrementalStore);

            assertThat(incrementalStore.entries()).isEqualTo(store.entries());
        } finally {
            TempDirectories.deleteRecursively(incrementalProjectRoot);
        }
    }

    private void addMergedPullRequest(int number, String... files) {
        addMergedPullRequest(transport, number, files);
    }

    private static void addMergedPullRequest(FakeGitHubTransport transport, int number, String... files) {
        transport.addClosedPullRequest(REPOSITORY, number, "PR " + number, true);
        registerContent(transport, number, files);
    }

    private void addRejectedPullRequest(int number, String... files) {
        transport.addClosedPullRequest(REPOSITORY, number, "PR " + number, false);
        registerContent(transport, number, files);
    }

    private void addOpenPullRequest(int number, String... files) {
        transport.addOpenPullRequest(REPOSITORY, number, "PR " + number);
        registerContent(transport, number, files);
    }

    private static void registerContent(FakeGitHubTransport transport, int number, String... files) {
        transport.addPullRequestDetail(REPOSITORY, number, "PR " + number, "octocat",
                "base-" + number, "head-" + number);
        for (String file : files) {
            transport.addChangedFile(REPOSITORY, number, file, "modified", null);
        }
    }
}
