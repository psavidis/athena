package com.athena.contextrewind;

import com.athena.git.TempDirectories;
import com.athena.github.FakeGitHubTransport;
import com.athena.github.GitHubRepositoryProvider;
import com.athena.knowledge.ObsidianKnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeProviderConfiguration;
import com.athena.memory.MemoryEntry;
import com.athena.memory.ProjectMemoryStore;
import com.athena.repository.RepositoryProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit tests for {@link ContextRewindService} (ticket #161) —
 * exercised through its public API against real collaborators: a real
 * {@link GitHubRepositoryProvider} (over the established
 * {@link FakeGitHubTransport} network-boundary fake), a real
 * {@link ProjectMemoryStore} backed by a temp directory, and a real
 * {@link ObsidianKnowledgeProvider} reading an actual temp vault. Only
 * {@link ContextNarrativeProvider} is faked, the one legitimate AI-network
 * boundary this ticket introduces.
 */
class ContextRewindServiceTest {

    private static final String TOKEN = "test-token";
    private static final String REPOSITORY = "acme/widgets";

    private final FakeGitHubTransport transport = new FakeGitHubTransport();
    private final RepositoryProvider repositoryProvider = new GitHubRepositoryProvider(TOKEN, transport);
    private final FakeContextNarrativeProvider narrativeProvider = new FakeContextNarrativeProvider();

    private Path projectRoot;
    private ProjectMemoryStore memoryStore;

    @BeforeEach
    void createProject() throws IOException {
        transport.acceptToken(TOKEN, "octocat");
        projectRoot = Files.createTempDirectory("athena-context-rewind-service-test-");
        memoryStore = new ProjectMemoryStore(projectRoot);
    }

    @AfterEach
    void cleanUp() {
        TempDirectories.deleteRecursively(projectRoot);
    }

    @Test
    void aggregatesGitPullRequestAndMemoryHistoryLabeledAsHistoricalFacts() {
        registerMergedPullRequestTouching(217, "Add retry handling", "PaymentProcessor.java");
        memoryStore.record(new MemoryEntry("PaymentProcessor.java and RetryWorker.java change together",
                "3 commits", "medium", false));

        ContextRewindService service = new ContextRewindService(
                repositoryProvider, memoryStore, Map.of(), Optional.empty());
        ReconstructedContext context = service.reconstruct(
                ContextRewindRequest.of("PaymentProcessor", projectRoot, REPOSITORY));

        assertThat(context.facts())
                .extracting(ContextFact::description)
                .anyMatch(description -> description.contains("217"))
                .anyMatch(description -> description.contains("change together"));
        assertThat(context.facts()).extracting(ContextFact::source).containsOnly(ContextSource.HISTORY);
        assertThat(context.pullRequestReferences()).containsExactly(new PullRequestReference(217, REPOSITORY));
    }

    @Test
    void labelsKnowledgeProviderInformationSeparatelyFromHistory() throws IOException {
        Path vault = Files.createTempDirectory("athena-context-rewind-service-test-vault-");
        try {
            Files.writeString(vault.resolve("Ownership.md"), "# Ownership\n\nPaymentProcessor is owned by the payments team.\n");
            KnowledgeProvider provider = new ObsidianKnowledgeProvider();
            KnowledgeProviderConfiguration configuration = KnowledgeProviderConfiguration.enabled(
                    Map.of(ObsidianKnowledgeProvider.VAULT_PATH_SETTING, vault.toString()));

            ContextRewindService service = new ContextRewindService(
                    repositoryProvider, memoryStore, Map.of(provider, configuration), Optional.empty());
            ReconstructedContext context = service.reconstruct(
                    ContextRewindRequest.of("PaymentProcessor", projectRoot, REPOSITORY));

            assertThat(context.facts())
                    .filteredOn(fact -> fact.source() == ContextSource.KNOWLEDGE_BASE)
                    .extracting(ContextFact::description)
                    .containsExactly("Ownership");
        } finally {
            TempDirectories.deleteRecursively(vault);
        }
    }

    @Test
    void functionsNormallyWithNoKnowledgeProviderConfigured() {
        registerMergedPullRequestTouching(217, "Add retry handling", "PaymentProcessor.java");

        ContextRewindService service = new ContextRewindService(
                repositoryProvider, memoryStore, Map.of(), Optional.empty());
        ReconstructedContext context = service.reconstruct(
                ContextRewindRequest.of("PaymentProcessor", projectRoot, REPOSITORY));

        assertThat(context.facts()).noneMatch(fact -> fact.source() == ContextSource.KNOWLEDGE_BASE);
        assertThat(context.hasInsufficientHistory()).isFalse();
    }

    @Test
    void reportsInsufficientHistoryInsteadOfFabricatingContext() {
        ContextRewindService service = new ContextRewindService(
                repositoryProvider, memoryStore, Map.of(), Optional.of(narrativeProvider));
        ReconstructedContext context = service.reconstruct(
                ContextRewindRequest.of("UntouchedHelper", projectRoot, REPOSITORY));

        assertThat(context.hasInsufficientHistory()).isTrue();
        assertThat(context.insufficientHistoryMessage()).contains(
                "Not enough historical information is available for UntouchedHelper");
        assertThat(context.aiNarrative()).isEmpty();
    }

    @Test
    void includesAiNarrativeLabeledAsInterpretationWhenHistoryExists() {
        registerMergedPullRequestTouching(217, "Add retry handling", "PaymentProcessor.java");
        narrativeProvider.willReturnNarrative("PaymentProcessor grew retry handling over time.");

        ContextRewindService service = new ContextRewindService(
                repositoryProvider, memoryStore, Map.of(), Optional.of(narrativeProvider));
        ReconstructedContext context = service.reconstruct(
                ContextRewindRequest.of("PaymentProcessor", projectRoot, REPOSITORY));

        assertThat(context.aiNarrative()).contains("PaymentProcessor grew retry handling over time.");
        assertThat(narrativeProvider.lastEntityName()).isEqualTo("PaymentProcessor");
    }

    @Test
    void catchUpScopesActivityToAfterTheGivenPoint() throws IOException, InterruptedException {
        initRepo();
        commitFile("PaymentProcessor.java", "add processor", "2025-12-01T10:00:00+00:00");
        commitFile("PaymentProcessor.java", "add retry handling", "2026-02-01T10:00:00+00:00");

        ContextRewindService service = new ContextRewindService(
                repositoryProvider, memoryStore, Map.of(), Optional.empty());
        ReconstructedContext context = service.reconstruct(
                ContextRewindRequest.of("PaymentProcessor", projectRoot, REPOSITORY)
                        .since(Instant.parse("2026-01-01T00:00:00Z")));

        assertThat(context.evolutionTimeline())
                .extracting(HistoricalActivity::description)
                .anyMatch(description -> description.contains("add retry handling"))
                .noneMatch(description -> description.contains("add processor"));
    }

    @Test
    void ordersEvolutionTimelineChronologically() throws IOException, InterruptedException {
        initRepo();
        commitFile("PaymentProcessor.java", "add processor", "2025-12-01T10:00:00+00:00");
        commitFile("PaymentProcessor.java", "add retry handling", "2026-02-01T10:00:00+00:00");

        ContextRewindService service = new ContextRewindService(
                repositoryProvider, memoryStore, Map.of(), Optional.empty());
        ReconstructedContext context = service.reconstruct(
                ContextRewindRequest.of("PaymentProcessor", projectRoot, REPOSITORY));

        assertThat(context.evolutionTimeline()).hasSize(2);
        assertThat(context.evolutionTimeline().get(0).description()).contains("add processor");
        assertThat(context.evolutionTimeline().get(1).description()).contains("add retry handling");
        assertThat(context.evolutionTimeline().get(0).occurredAt())
                .isBefore(context.evolutionTimeline().get(1).occurredAt());
    }

    private void registerMergedPullRequestTouching(int number, String title, String changedFile) {
        transport.addClosedPullRequest(REPOSITORY, number, title, true);
        transport.addPullRequestDetail(REPOSITORY, number, title, "octocat", "main", "feature");
        transport.addChangedFile(REPOSITORY, number, changedFile, "modified", null);
    }

    private void initRepo() throws IOException, InterruptedException {
        runGit("init", "--quiet");
        runGit("config", "user.email", "test@example.com");
        runGit("config", "user.name", "Test");
    }

    private void commitFile(String fileName, String message, String isoCommitDate) throws IOException, InterruptedException {
        Path file = projectRoot.resolve(fileName);
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
