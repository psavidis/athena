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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
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
 *
 * <p><b>Known limitation (ticket #189):</b> the optional {@code since}
 * parameter only scopes the git-derived evolution timeline —
 * {@link ContextRewindService}'s Pull Request lookup has no date to scope
 * by, since neither {@code ImportedPullRequest} nor
 * {@code ClosedPullRequestSummary} carries one. A "catch me up" response's
 * {@code pullRequestReferences} is therefore always the full, unscoped
 * list, not limited to Pull Requests since the given point.
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
        this(session, GitHubRepositoryProvider::new, new AiKeyStore(), null, new KnowledgeProviderResolver());
    }

    /** Test seam: a fake {@link RepositoryProvider} factory, a fixed narrative provider, and/or a
     * {@link KnowledgeProviderResolver} backed by a temp config file stand in for GitHub, the
     * AI-network boundary, and persisted Knowledge Provider configuration. {@code
     * fixedNarrativeProvider} being {@code null} means "resolve one from a configured API key" —
     * a test wanting "no key configured" instead must use {@link #ContextRewindController(WebSession,
     * Function, AiKeyStore, ContextNarrativeProvider, KnowledgeProviderResolver)} with an
     * {@link AiKeyStore} pointed at an empty temp file, the same way {@code AiAnalysisController}'s
     * own test seam takes an explicit {@link AiKeyStore} rather than always defaulting to this
     * machine's real {@code ~/.athena/claude.json}. */
    ContextRewindController(WebSession session, Function<String, RepositoryProvider> repositoryProviderFactory,
                             ContextNarrativeProvider fixedNarrativeProvider, KnowledgeProviderResolver knowledgeResolver) {
        this(session, repositoryProviderFactory, new AiKeyStore(), fixedNarrativeProvider, knowledgeResolver);
    }

    /** Test seam: as above, plus an explicit {@link AiKeyStore} — so a test exercising the
     * "no AI narrative provider configured" path never depends on this machine's own saved key. */
    ContextRewindController(WebSession session, Function<String, RepositoryProvider> repositoryProviderFactory,
                             AiKeyStore keyStore, ContextNarrativeProvider fixedNarrativeProvider,
                             KnowledgeProviderResolver knowledgeResolver) {
        this.session = session;
        this.repositoryProviderFactory = repositoryProviderFactory;
        this.fixedNarrativeProvider = fixedNarrativeProvider;
        this.keyStore = keyStore;
        this.knowledgeResolver = knowledgeResolver;
    }

    @GetMapping("/api/review/context-rewind/{entityName}")
    public ContextRewindResponse contextRewind(@PathVariable String entityName,
                                                @RequestParam(required = false) String since) {
        String token = session.gitHubToken()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub"));
        WebSession.SelectedPullRequest selection = session.selectedPullRequest()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No pull request selected"));

        RepositoryProvider repositoryProvider = repositoryProviderFactory.apply(token);
        ContextRewindService service = new ContextRewindService(repositoryProvider,
                new ProjectMemoryStore(selection.headRoot()), knowledgeProviders(), Optional.ofNullable(narrativeProvider()));

        ContextRewindRequest request = ContextRewindRequest.of(entityName, selection.headRoot(), selection.repositoryFullName());
        if (since != null) {
            request = request.since(parseSince(since));
        }

        ReconstructedContext context = service.reconstruct(request);
        return ContextRewindResponse.from(context);
    }

    /** A "catch me up" scoping point (ticket #189) — a developer-chosen date, since Athena
     * tracks no per-developer last-interaction timestamp to auto-detect one from. */
    private static Instant parseSince(String since) {
        try {
            return Instant.parse(since);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "since must be a valid instant");
        }
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
