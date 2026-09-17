package com.athena.reviewbriefing;

import com.athena.contextrewind.ContextRewindService;
import com.athena.git.TempDirectories;
import com.athena.github.FakeGitHubTransport;
import com.athena.github.GitHubRepositoryProvider;
import com.athena.memory.ProjectMemoryStore;
import com.athena.plugins.PluginRegistry;
import com.athena.repository.RepositoryProvider;
import com.athena.reviewui.JavaFixtureSupport;
import com.athena.semantic.AnalysisResult;
import com.athena.semantic.Change;
import com.athena.semantic.PrAnalyzer;
import com.athena.semantic.SemanticProfile;
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
 * Dedicated unit test for {@link ReviewBriefingService} (ticket #223) —
 * the composition of the four generators #219-222 each built. Uses the
 * fake providers those tickets already established for the AI-network
 * boundary, and a real {@link ContextRewindService} (matching {@code
 * HistoricalContextGeneratorTest}'s own established fixture pattern) for
 * the historical/Knowledge Base half.
 */
class ReviewBriefingServiceTest {

    private static final String TOKEN = "test-token";
    private static final String REPOSITORY = "acme/widgets";

    private final FakeGitHubTransport transport = new FakeGitHubTransport();
    private final RepositoryProvider repositoryProvider = new GitHubRepositoryProvider(TOKEN, transport);
    private final FakeSemanticChangeSummaryProvider summaryProvider = new FakeSemanticChangeSummaryProvider();
    private final FakeUncertaintyAndQuestionsProvider uncertaintyProvider = new FakeUncertaintyAndQuestionsProvider();

    private Path projectRoot;
    private ProjectMemoryStore memoryStore;

    @BeforeEach
    void createProject() throws IOException {
        transport.acceptToken(TOKEN, "octocat");
        projectRoot = Files.createTempDirectory("athena-review-briefing-service-test-");
        memoryStore = new ProjectMemoryStore(projectRoot);
    }

    @AfterEach
    void cleanUp() {
        TempDirectories.deleteRecursively(projectRoot);
    }

    @Test
    void producesAnEmptyBriefingForNoChanges() {
        ReviewBriefingService service = newService();

        ReviewBriefing briefing = service.generate(List.of(), List.of(), projectRoot, REPOSITORY);

        assertThat(briefing.changeSummary()).isEmpty();
        assertThat(briefing.focusAreas()).isEmpty();
        assertThat(briefing.uncertainties()).isEmpty();
        assertThat(briefing.questions()).isEmpty();
        assertThat(briefing.historicalContext()).isEmpty();
        assertThat(briefing.relevantKnowledge()).isEmpty();
        assertThat(summaryProvider.callCount()).isZero();
        assertThat(uncertaintyProvider.callCount()).isZero();
    }

    @Test
    void neverSetsARecommendedStartingPointSinceNoGeneratorProducesOne() {
        ReviewBriefingService service = newService();

        ReviewBriefing briefing = service.generate(List.of(), List.of(), projectRoot, REPOSITORY);

        assertThat(briefing.recommendedStartingPoint()).isEmpty();
    }

    @Test
    void composesRealContentFromAllFourGeneratorsForARealChange() throws IOException {
        Path baseRoot = Files.createTempDirectory("athena-review-briefing-service-test-base-");
        Path headRoot = Files.createTempDirectory("athena-review-briefing-service-test-head-");
        try {
            JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
            PrAnalyzer analyzer = new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins());
            AnalysisResult analysisResult = analyzer.analyze(baseRoot, headRoot);
            List<Change> changes = analysisResult.changes();
            List<SemanticProfile> profiles = changes.stream().map(analysisResult::semanticProfileFor).toList();
            summaryProvider.willReturnSummary("Renamed a method on Greeter.");
            uncertaintyProvider.willReturn(new UncertaintyAndQuestions(
                    List.of(BriefingItem.of("Unclear why this was renamed", "Greeter")), List.of()));

            ReviewBriefingService service = newService();
            ReviewBriefing briefing = service.generate(changes, profiles, projectRoot, REPOSITORY);

            assertThat(briefing.changeSummary()).isPresent();
            assertThat(briefing.changeSummary().get().description()).isEqualTo("Renamed a method on Greeter.");
            assertThat(briefing.focusAreas()).isNotEmpty();
            assertThat(briefing.uncertainties()).isNotEmpty();
            assertThat(summaryProvider.callCount()).isEqualTo(1);
            assertThat(uncertaintyProvider.callCount()).isEqualTo(1);
        } finally {
            TempDirectories.deleteRecursively(baseRoot);
            TempDirectories.deleteRecursively(headRoot);
        }
    }

    private ReviewBriefingService newService() {
        ContextRewindService contextRewindService =
                new ContextRewindService(repositoryProvider, memoryStore, Map.of(), Optional.empty());
        return new ReviewBriefingService(
                new ChangeSummaryGenerator(summaryProvider),
                new FocusAreaGenerator(),
                new UncertaintyAndQuestionsGenerator(uncertaintyProvider),
                new HistoricalContextGenerator(contextRewindService));
    }
}
