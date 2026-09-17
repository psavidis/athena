package com.athena.reviewbriefing;

import com.athena.contextrewind.ContextRewindService;
import com.athena.git.TempDirectories;
import com.athena.github.FakeGitHubTransport;
import com.athena.github.GitHubRepositoryProvider;
import com.athena.knowledge.ObsidianKnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeProviderConfiguration;
import com.athena.memory.ProjectMemoryStore;
import com.athena.repository.RepositoryProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit test for {@link HistoricalContextGenerator} (ticket
 * #222) — exercised through its public API against a real {@link
 * ContextRewindService}, the same established fixture pattern {@code
 * ContextRewindServiceTest} already uses (real {@link
 * GitHubRepositoryProvider} over {@link FakeGitHubTransport}, real
 * {@link ProjectMemoryStore}, real {@link ObsidianKnowledgeProvider}).
 */
class HistoricalContextGeneratorTest {

    private static final String TOKEN = "test-token";
    private static final String REPOSITORY = "acme/widgets";

    private final FakeGitHubTransport transport = new FakeGitHubTransport();
    private final RepositoryProvider repositoryProvider = new GitHubRepositoryProvider(TOKEN, transport);

    private Path projectRoot;
    private ProjectMemoryStore memoryStore;

    @BeforeEach
    void createProject() throws IOException {
        transport.acceptToken(TOKEN, "octocat");
        projectRoot = Files.createTempDirectory("athena-historical-context-test-");
        memoryStore = new ProjectMemoryStore(projectRoot);
    }

    @AfterEach
    void cleanUp() {
        TempDirectories.deleteRecursively(projectRoot);
    }

    @Test
    void surfacesHistoricalContextForAnEntityWithPriorPullRequests() {
        registerMergedPullRequestTouching(217, "Add retry handling", "PaymentProcessor.java");
        HistoricalContextGenerator generator = new HistoricalContextGenerator(
                new ContextRewindService(repositoryProvider, memoryStore, Map.of(), Optional.empty()));

        HistoricalContext result = generator.generate(List.of("PaymentProcessor"), projectRoot, REPOSITORY);

        assertThat(result.historicalContext()).isNotEmpty();
        assertThat(result.historicalContext().get(0).entityReference()).contains("PaymentProcessor");
        assertThat(result.relevantKnowledge()).isEmpty();
    }

    @Test
    void surfacesRelevantKnowledgeForAnEntityWithAKnowledgeBaseEntry() throws IOException {
        Path vault = Files.createTempDirectory("athena-historical-context-test-vault-");
        try {
            Files.writeString(vault.resolve("Ownership.md"), "# Ownership\n\nPaymentProcessor is owned by the payments team.\n");
            KnowledgeProvider provider = new ObsidianKnowledgeProvider();
            KnowledgeProviderConfiguration configuration = KnowledgeProviderConfiguration.enabled(
                    Map.of(ObsidianKnowledgeProvider.VAULT_PATH_SETTING, vault.toString()));
            HistoricalContextGenerator generator = new HistoricalContextGenerator(new ContextRewindService(
                    repositoryProvider, memoryStore, Map.of(provider, configuration), Optional.empty()));

            HistoricalContext result = generator.generate(List.of("PaymentProcessor"), projectRoot, REPOSITORY);

            assertThat(result.relevantKnowledge()).isNotEmpty();
            assertThat(result.relevantKnowledge().get(0).entityReference()).contains("PaymentProcessor");
        } finally {
            TempDirectories.deleteRecursively(vault);
        }
    }

    @Test
    void contributesNothingForAnEntityWithNoHistoryOrKnowledge() {
        HistoricalContextGenerator generator = new HistoricalContextGenerator(
                new ContextRewindService(repositoryProvider, memoryStore, Map.of(), Optional.empty()));

        HistoricalContext result = generator.generate(List.of("Untouched"), projectRoot, REPOSITORY);

        assertThat(result.historicalContext()).isEmpty();
        assertThat(result.relevantKnowledge()).isEmpty();
    }

    @Test
    void producesNothingForNoFocusAreaEntities() {
        HistoricalContextGenerator generator = new HistoricalContextGenerator(
                new ContextRewindService(repositoryProvider, memoryStore, Map.of(), Optional.empty()));

        HistoricalContext result = generator.generate(List.of(), projectRoot, REPOSITORY);

        assertThat(result.historicalContext()).isEmpty();
        assertThat(result.relevantKnowledge()).isEmpty();
    }

    private void registerMergedPullRequestTouching(int number, String title, String changedFile) {
        transport.addClosedPullRequest(REPOSITORY, number, title, true);
        transport.addPullRequestDetail(REPOSITORY, number, title, "octocat", "main", "feature");
        transport.addChangedFile(REPOSITORY, number, changedFile, "modified", null);
    }
}
