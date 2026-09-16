package com.athena.web.contextrewind;

import com.athena.ai.AiKeyStore;
import com.athena.contextrewind.ClaudeContextNarrativeProvider;
import com.athena.contextrewind.ContextNarrativeProvider;
import com.athena.contextrewind.ContextRewindRequest;
import com.athena.contextrewind.ContextRewindService;
import com.athena.contextrewind.ReconstructedContext;
import com.athena.github.GitHubRepositoryProvider;
import com.athena.knowledge.ConfiguredKnowledgeProvider;
import com.athena.knowledge.KnowledgeProviderResolver;
import com.athena.knowledge.spi.KnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeProviderConfiguration;
import com.athena.memory.ProjectMemoryStore;
import com.athena.repository.RepositoryProvider;
import com.athena.web.WebSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Reconstructs and serves a code entity's Context Rewind (ticket #187, the
 * foundational view for ticket #162's UX), exposing {@link ContextRewindService}
 * (ticket #161) over HTTP the way {@code AiAnalysisController} exposes AI
 * analysis. Unlike most read-only endpoints in this web layer, this one
 * requires a selected Pull Request specifically (not a standalone Diff): a
 * repository is a hard requirement of {@link ContextRewindRequest}, and only
 * a Pull Request selection carries one.
 *
 * <p><b>Known limitation:</b> {@code ContextRewindService}'s evolution
 * timeline reads git history from the given project root via the system
 * {@code git} binary. The session's own checkout ({@link WebSession.SelectedPullRequest#headRoot()})
 * comes from {@link com.athena.git.GitRevisionCheckout}, a deliberately
 * shallow, single-revision, depth-1 fetch — so the git-commit-derived part
 * of the timeline will typically be thin or empty against a real session,
 * even though Pull Request references, memory facts, knowledge items, and
 * the AI narrative are unaffected (none of those depend on local git
 * history depth). Giving the session a full-history local clone is a
 * separate, not-yet-built capability, left to a follow-up rather than
 * solved here.
 */
@RestController
public class ContextRewindController {

    private final WebSession session;
    private final Function<String, RepositoryProvider> repositoryProviderFactory;
    private final ContextNarrativeProvider fixedNarrativeProvider;
    private final AiKeyStore keyStore;
    private final KnowledgeProviderResolver knowledgeResolver;

    @Autowired
    public ContextRewindController(WebSession session) {
        this(session, GitHubRepositoryProvider::new, null);
    }

    /** Test seam: a fake {@link RepositoryProvider} factory and/or a fixed narrative provider
     * stand in for GitHub and the AI-network boundary. */
    ContextRewindController(WebSession session, Function<String, RepositoryProvider> repositoryProviderFactory,
                             ContextNarrativeProvider fixedNarrativeProvider) {
        this.session = session;
        this.repositoryProviderFactory = repositoryProviderFactory;
        this.fixedNarrativeProvider = fixedNarrativeProvider;
        this.keyStore = new AiKeyStore();
        this.knowledgeResolver = new KnowledgeProviderResolver();
    }

    @GetMapping("/api/review/context-rewind/{entityName}")
    public ContextRewindResponse contextRewind(@PathVariable String entityName) {
        String token = session.gitHubToken()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub"));
        WebSession.SelectedPullRequest selection = session.selectedPullRequest()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No pull request selected"));

        RepositoryProvider repositoryProvider = repositoryProviderFactory.apply(token);
        ContextRewindService service = new ContextRewindService(repositoryProvider,
                new ProjectMemoryStore(selection.headRoot()), knowledgeProviders(), Optional.ofNullable(narrativeProvider()));

        ReconstructedContext context = service.reconstruct(
                ContextRewindRequest.of(entityName, selection.headRoot(), selection.repositoryFullName()));
        return ContextRewindResponse.from(context);
    }

    private Map<KnowledgeProvider, KnowledgeProviderConfiguration> knowledgeProviders() {
        return knowledgeResolver.resolve().map(ConfiguredKnowledgeProvider::asProviderMap).orElse(Map.of());
    }

    /** Resolved fresh on every call, the same as {@code AiAnalysisController#provider()} — a key
     * saved via "Connect Claude" takes effect immediately, with no restart. */
    private ContextNarrativeProvider narrativeProvider() {
        if (fixedNarrativeProvider != null) {
            return fixedNarrativeProvider;
        }
        return apiKey().map(ContextRewindController::buildNarrativeProvider).orElse(null);
    }

    private Optional<String> apiKey() {
        String envKey = System.getenv("ANTHROPIC_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return Optional.of(envKey);
        }
        return keyStore.loadApiKey();
    }

    private static ContextNarrativeProvider buildNarrativeProvider(String apiKey) {
        return new ClaudeContextNarrativeProvider(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), apiKey);
    }
}
