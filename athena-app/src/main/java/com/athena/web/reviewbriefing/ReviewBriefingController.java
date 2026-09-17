package com.athena.web.reviewbriefing;

import com.athena.ai.AiKeyStore;
import com.athena.contextrewind.ContextRewindService;
import com.athena.github.GitHubRepositoryProvider;
import com.athena.knowledge.ConfiguredKnowledgeProvider;
import com.athena.knowledge.KnowledgeProviderResolver;
import com.athena.knowledge.spi.KnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeProviderConfiguration;
import com.athena.memory.ProjectMemoryStore;
import com.athena.repository.RepositoryProvider;
import com.athena.reviewbriefing.ChangeSummaryGenerator;
import com.athena.reviewbriefing.ClaudeSemanticChangeSummaryProvider;
import com.athena.reviewbriefing.ClaudeUncertaintyAndQuestionsProvider;
import com.athena.reviewbriefing.FocusAreaGenerator;
import com.athena.reviewbriefing.HistoricalContextGenerator;
import com.athena.reviewbriefing.ReviewBriefing;
import com.athena.reviewbriefing.ReviewBriefingService;
import com.athena.reviewbriefing.SemanticChangeSummaryProvider;
import com.athena.reviewbriefing.UncertaintyAndQuestionsGenerator;
import com.athena.reviewbriefing.UncertaintyAndQuestionsProvider;
import com.athena.semantic.Change;
import com.athena.semantic.SemanticProfile;
import com.athena.web.Diff;
import com.athena.web.WebSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Composes and serves the currently-selected PR's full {@link
 * ReviewBriefing} (ticket #223 — this endpoint is the backend
 * prerequisite none of #219-222 built), reusing {@link
 * ContextRewindService} the same way {@code ContextRewindController}
 * already wires it, and Claude-backed providers the same way {@code
 * AiAnalysisController}/{@code ContextRewindController} resolve an API
 * key fresh on every call.
 */
@RestController
public class ReviewBriefingController {

    private final WebSession session;
    private final Function<String, RepositoryProvider> repositoryProviderFactory;
    private final AiKeyStore keyStore;
    private final KnowledgeProviderResolver knowledgeResolver;
    private final SemanticChangeSummaryProvider fixedSummaryProvider;
    private final UncertaintyAndQuestionsProvider fixedUncertaintyProvider;

    @Autowired
    public ReviewBriefingController(WebSession session) {
        this(session, GitHubRepositoryProvider::new, new KnowledgeProviderResolver(), null, null);
    }

    /** Test seam: a fake {@link RepositoryProvider} factory (matching {@code
     * ContextRewindController}'s own precedent), a {@link KnowledgeProviderResolver} backed by a
     * temp config file, and/or fixed AI providers standing in for the network boundary a real API
     * key would otherwise require. */
    ReviewBriefingController(WebSession session, Function<String, RepositoryProvider> repositoryProviderFactory,
                              KnowledgeProviderResolver knowledgeResolver,
                              SemanticChangeSummaryProvider fixedSummaryProvider,
                              UncertaintyAndQuestionsProvider fixedUncertaintyProvider) {
        this.session = session;
        this.repositoryProviderFactory = repositoryProviderFactory;
        this.keyStore = new AiKeyStore();
        this.knowledgeResolver = knowledgeResolver;
        this.fixedSummaryProvider = fixedSummaryProvider;
        this.fixedUncertaintyProvider = fixedUncertaintyProvider;
    }

    @GetMapping("/api/review-briefings")
    public ReviewBriefingResponse reviewBriefing() {
        String token = session.gitHubToken()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub"));
        WebSession.SelectedPullRequest selection = session.selectedPullRequest()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No pull request selected"));

        Diff diff = selection.diff();
        List<Change> changes = diff.changes();
        List<SemanticProfile> profiles = changes.stream().map(diff::semanticProfileFor).toList();

        RepositoryProvider repositoryProvider = repositoryProviderFactory.apply(token);
        ContextRewindService contextRewindService = new ContextRewindService(repositoryProvider,
                new ProjectMemoryStore(selection.headRoot()), knowledgeProviders(), Optional.empty());

        ReviewBriefingService service = new ReviewBriefingService(
                new ChangeSummaryGenerator(summaryProvider()),
                new FocusAreaGenerator(),
                new UncertaintyAndQuestionsGenerator(uncertaintyProvider()),
                new HistoricalContextGenerator(contextRewindService));

        ReviewBriefing briefing = service.generate(changes, profiles, selection.headRoot(), selection.repositoryFullName());
        return ReviewBriefingResponse.of(briefing);
    }

    private SemanticChangeSummaryProvider summaryProvider() {
        if (fixedSummaryProvider != null) {
            return fixedSummaryProvider;
        }
        return apiKey()
                .<SemanticChangeSummaryProvider>map(key -> new ClaudeSemanticChangeSummaryProvider(httpClient(), key))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No AI provider configured"));
    }

    private UncertaintyAndQuestionsProvider uncertaintyProvider() {
        if (fixedUncertaintyProvider != null) {
            return fixedUncertaintyProvider;
        }
        return apiKey()
                .<UncertaintyAndQuestionsProvider>map(key -> new ClaudeUncertaintyAndQuestionsProvider(httpClient(), key))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No AI provider configured"));
    }

    private HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    private Map<KnowledgeProvider, KnowledgeProviderConfiguration> knowledgeProviders() {
        return knowledgeResolver.resolve().map(ConfiguredKnowledgeProvider::asProviderMap).orElse(Map.of());
    }

    private Optional<String> apiKey() {
        String envKey = System.getenv("ANTHROPIC_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return Optional.of(envKey);
        }
        return keyStore.loadApiKey();
    }
}
