package com.athena.memory;

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

        PullRequestHistoryLearner.learn(REPOSITORY, provider, store);

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

        PullRequestHistoryLearner.learn(REPOSITORY, provider, store);

        assertThat(store.entries()).isEmpty();
    }

    @Test
    void recordsAClosedWithoutMergingPullRequestAsARejectedChangeNotAnAcceptedPattern() {
        addRejectedPullRequest(42, "LegacyExport.java", "LegacyImport.java");

        PullRequestHistoryLearner.learn(REPOSITORY, provider, store);

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

        PullRequestHistoryLearner.learn(REPOSITORY, provider, store);

        MemoryEntry entry = store.entries().stream()
                .filter(e -> e.fact().contains("Checkout.java") && e.fact().contains("Payment.java"))
                .findFirst()
                .orElseThrow();
        assertThat(entry.confidence()).isEqualTo("provisional");
    }

    @Test
    void producesNoMemoryWhenThereAreNoPullRequestsYet() {
        assertThatCode(() -> PullRequestHistoryLearner.learn(REPOSITORY, provider, store))
                .doesNotThrowAnyException();

        assertThat(store.entries()).isEmpty();
    }

    @Test
    void doesNotDuplicateAPairAlreadyLearnedOnAnEarlierRun() {
        addMergedPullRequest(1, "OrderService.java", "OrderProjection.java");
        addMergedPullRequest(2, "OrderService.java", "OrderProjection.java");
        PullRequestHistoryLearner.learn(REPOSITORY, provider, store);

        PullRequestHistoryLearner.learn(REPOSITORY, provider, store);

        assertThat(store.entries())
                .filteredOn(e -> e.fact().contains("OrderService.java") && e.fact().contains("OrderProjection.java"))
                .hasSize(1);
    }

    private void addMergedPullRequest(int number, String... files) {
        transport.addClosedPullRequest(REPOSITORY, number, "PR " + number, true);
        registerContent(number, files);
    }

    private void addRejectedPullRequest(int number, String... files) {
        transport.addClosedPullRequest(REPOSITORY, number, "PR " + number, false);
        registerContent(number, files);
    }

    private void addOpenPullRequest(int number, String... files) {
        transport.addOpenPullRequest(REPOSITORY, number, "PR " + number);
        registerContent(number, files);
    }

    private void registerContent(int number, String... files) {
        transport.addPullRequestDetail(REPOSITORY, number, "PR " + number, "octocat",
                "base-" + number, "head-" + number);
        for (String file : files) {
            transport.addChangedFile(REPOSITORY, number, file, "modified", null);
        }
    }
}
