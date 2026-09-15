package com.athena.web.ai;

import com.athena.ai.AiAnalysisOrchestrator;
import com.athena.ai.AiContextBoundary;
import com.athena.ai.AiFindingItem;
import com.athena.ai.AiFindingsBoard;
import com.athena.ai.AiKeyStore;
import com.athena.ai.AiProvider;
import com.athena.ai.ClaudeAiProvider;
import com.athena.ai.ClaudeCliProvider;
import com.athena.knowledge.ConfiguredKnowledgeProvider;
import com.athena.knowledge.KnowledgeProviderResolver;
import com.athena.knowledge.KnowledgeRetriever;
import com.athena.knowledge.spi.KnowledgeCandidate;
import com.athena.knowledge.spi.KnowledgeCaptureResult;
import com.athena.knowledge.spi.KnowledgeItem;
import com.athena.knowledge.spi.KnowledgeQuery;
import com.athena.reviewui.ChangeDetailView;
import com.athena.semantic.Change;
import com.athena.web.ChangeKey;
import com.athena.web.WebSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * Inspecting the AI context boundary and triggering AI analysis of a
 * completed review, then independently evaluating each surfaced finding
 * (ticket #77). Reuses {@link AiAnalysisOrchestrator}, {@link AiContextBoundary},
 * {@link AiProvider}/{@link ClaudeAiProvider}, {@link com.athena.ai.AiFindingsBoard},
 * {@link AiFindingItem} unmodified.
 */
@RestController
public class AiAnalysisController {

    private final WebSession session;
    private final AiKeyStore keyStore;
    private final AiProvider fixedProvider;
    private final KnowledgeProviderResolver knowledgeResolver;

    @Autowired
    public AiAnalysisController(WebSession session) {
        this(session, new AiKeyStore(), null, new KnowledgeProviderResolver());
    }

    /** Test seam: a fake {@link AiProvider} stands in for the real network boundary. */
    AiAnalysisController(WebSession session, AiProvider fixedProvider) {
        this(session, new AiKeyStore(), fixedProvider, new KnowledgeProviderResolver());
    }

    /**
     * Test seam: as above, plus an explicit {@link KnowledgeProviderResolver} (ticket #118) —
     * typically backed by a {@code KnowledgeProviderStore} pointed at a temp file, so a test's
     * "Knowledge Provider configured/not configured" fixture never depends on this machine's own
     * {@code ~/.athena/knowledge.json}.
     */
    AiAnalysisController(WebSession session, AiProvider fixedProvider, KnowledgeProviderResolver knowledgeResolver) {
        this(session, new AiKeyStore(), fixedProvider, knowledgeResolver);
    }

    private AiAnalysisController(WebSession session, AiKeyStore keyStore, AiProvider fixedProvider,
                                  KnowledgeProviderResolver knowledgeResolver) {
        this.session = session;
        this.keyStore = keyStore;
        this.fixedProvider = fixedProvider;
        this.knowledgeResolver = knowledgeResolver;
    }

    /**
     * The configured {@link AiProvider}, or {@code null} if none is
     * available yet. Resolved fresh on every call — rather than cached at
     * construction — so a key saved via {@link #connect} through "Connect
     * Claude" takes effect immediately, with no restart. Checks, in order:
     * a test-injected {@link #fixedProvider}; the {@code claude} CLI, if
     * installed — reuses whoever's already-logged-in Claude Code
     * subscription with no separate API billing; the
     * {@code ANTHROPIC_API_KEY} environment variable; a key previously
     * saved via {@link AiKeyStore} through "Connect Claude".
     */
    private AiProvider provider() {
        if (fixedProvider != null) {
            return fixedProvider;
        }
        Optional<String> claudeExecutable = findClaudeExecutable();
        if (claudeExecutable.isPresent()) {
            return new ClaudeCliProvider(claudeExecutable.get());
        }
        return apiKey().map(AiAnalysisController::buildApiProvider).orElse(null);
    }

    /** Resolves the {@code claude} CLI on PATH, the same way a shell would. */
    private Optional<String> findClaudeExecutable() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return Optional.empty();
        }
        for (String dir : pathEnv.split(File.pathSeparator)) {
            File candidate = new File(dir, "claude");
            if (candidate.isFile() && candidate.canExecute()) {
                return Optional.of(candidate.getAbsolutePath());
            }
        }
        return Optional.empty();
    }

    private Optional<String> apiKey() {
        String envKey = System.getenv("ANTHROPIC_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return Optional.of(envKey);
        }
        return keyStore.loadApiKey();
    }

    private static AiProvider buildApiProvider(String apiKey) {
        return new ClaudeAiProvider(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), apiKey);
    }

    /**
     * Whether an AI provider is configured, so the frontend can surface
     * this upfront (e.g. disabling "AI analysis") instead of only finding
     * out when {@link #triggerAnalysis} 503s.
     */
    @GetMapping("/api/ai/status")
    public AiStatusResponse aiStatus() {
        return new AiStatusResponse(provider() != null);
    }

    /**
     * Saves a pasted Anthropic API key (the "Connect Claude" one-time
     * paste-back, since Anthropic has no App-installation-style flow to
     * provision one automatically) so it's never needed again.
     */
    @PostMapping("/api/ai/connect")
    public AiStatusResponse connect(@RequestBody ConnectAiRequest request) {
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "apiKey must not be blank");
        }
        keyStore.saveApiKey(request.apiKey().strip());
        return aiStatus();
    }

    public record ConnectAiRequest(String apiKey) {
    }

    @GetMapping("/api/review/ai-context-boundary")
    public AiContextBoundaryResponse contextBoundary() {
        WebSession.SelectedPullRequest selection = requireSelection();
        AiContextBoundary boundary = AiContextBoundary.assemble(selection.pullRequest().title(),
                selection.changes(), selection.reviewStateStore(), selection.annotationBoard());

        List<String> included = Stream.of(boundary.payload().reviewedChanges(), boundary.payload().skippedChanges(),
                        boundary.payload().mechanicalChanges(), boundary.payload().concernChanges())
                .flatMap(List::stream)
                .map(Change::title)
                .toList();

        return new AiContextBoundaryResponse(included, titlesOf(boundary.excludedUnreviewedChanges()),
                titlesOf(boundary.excludedGeneratedChanges()), boundary.privateNotesExcluded());
    }

    @PostMapping("/api/review/ai-analysis")
    public List<AiFindingResponse> triggerAnalysis() {
        AiProvider provider = requireAiProviderConfigured();
        WebSession.SelectedPullRequest selection = requireSelection();
        List<Change> changes = selection.changes();

        List<KnowledgeItem> knowledgeItems = retrieveKnowledge(selection, changes);
        AiFindingsBoard board = AiAnalysisOrchestrator.trigger(selection.pullRequest().title(), changes,
                selection.reviewStateStore(), selection.annotationBoard(), provider, knowledgeItems);
        session.setAiFindingsBoard(board);

        return findingResponses(board, changes);
    }

    /**
     * Retrieves knowledge relevant to this review from the configured Knowledge Provider
     * (ticket #118) — empty, with no error, when none is configured, or when the provider
     * itself fails, per {@link KnowledgeRetriever}'s own graceful-degradation contract. This is
     * the one place a review's AI analysis reaches for project knowledge, so "no provider
     * configured" and "provider failed" are indistinguishable to the rest of the review, exactly
     * as the ticket requires.
     */
    private List<KnowledgeItem> retrieveKnowledge(WebSession.SelectedPullRequest selection, List<Change> changes) {
        Optional<ConfiguredKnowledgeProvider> configured = knowledgeResolver.resolve();
        if (configured.isEmpty()) {
            return List.of();
        }
        KnowledgeRetriever retriever = new KnowledgeRetriever(configured.get().asProviderMap());
        KnowledgeQuery query = KnowledgeQuery.of(selection.repositoryFullName(), changedFilePaths(changes), List.of());
        return retriever.retrieve(query);
    }

    private List<String> changedFilePaths(List<Change> changes) {
        return changes.stream()
                .flatMap(change -> change.matchedOccurrences().stream())
                .flatMap(occurrence -> occurrence.filesTouched().stream())
                .distinct()
                .toList();
    }

    /**
     * Persists an AI finding the reviewer chose to keep as project knowledge (ticket #118's
     * "knowledge candidate" capture) — always a user-confirmed action, never automatic. Rejected
     * outright when no Knowledge Provider is configured, rather than silently doing nothing.
     */
    @PostMapping("/api/review/ai-findings/{findingId}/save-to-knowledge-base")
    public KnowledgeCaptureResponse saveToKnowledgeBase(@PathVariable String findingId) {
        WebSession.SelectedPullRequest selection = requireSelection();
        AiFindingsBoard board = session.aiFindingsBoard()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No AI analysis has been triggered yet"));
        AiFindingItem finding = requireFinding(board, findingId);
        ConfiguredKnowledgeProvider configured = knowledgeResolver.resolve()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "No Knowledge Provider is configured"));

        KnowledgeCandidate candidate = KnowledgeCandidate.withRationale(selection.repositoryFullName(),
                finding.description(), "Accepted AI finding", Instant.now());
        KnowledgeCaptureResult result = configured.provider().captureCandidate(candidate, configured.configuration());
        if (!result.isSuccessful()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, result.failureReason());
        }
        String storedAs = result.persistedItem().map(KnowledgeItem::source).orElse(null);
        return new KnowledgeCaptureResponse(true, configured.provider().providerId(), storedAs);
    }

    private AiFindingItem requireFinding(AiFindingsBoard board, String findingId) {
        return board.items().stream()
                .filter(item -> item.id().equals(findingId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Finding not found"));
    }

    public record KnowledgeCaptureResponse(boolean success, String providerId, String storedAs) {
    }

    @PostMapping("/api/review/ai-findings/{findingId}/accept")
    public List<AiFindingResponse> accept(@PathVariable String findingId) {
        return evaluate(findingId, AiFindingsBoard::accept);
    }

    @PostMapping("/api/review/ai-findings/{findingId}/dismiss")
    public List<AiFindingResponse> dismiss(@PathVariable String findingId) {
        return evaluate(findingId, AiFindingsBoard::dismiss);
    }

    private List<AiFindingResponse> evaluate(String findingId, BiConsumer<AiFindingsBoard, String> action) {
        WebSession.SelectedPullRequest selection = requireSelection();
        AiFindingsBoard board = session.aiFindingsBoard()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No AI analysis has been triggered yet"));
        try {
            action.accept(board, findingId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Finding not found");
        }
        return findingResponses(board, selection.changes());
    }

    private List<AiFindingResponse> findingResponses(AiFindingsBoard board, List<Change> changes) {
        return board.items().stream().map(item -> toResponse(item, changes)).toList();
    }

    private AiFindingResponse toResponse(AiFindingItem item, List<Change> changes) {
        String jumpTargetChangeKey = item.jumpTarget()
                .flatMap(target -> changeKeyFor(target, changes))
                .orElse(null);
        return new AiFindingResponse(item.id(), item.description(), item.disposition(), jumpTargetChangeKey);
    }

    private Optional<String> changeKeyFor(ChangeDetailView target, List<Change> changes) {
        return changes.stream()
                .filter(change -> change.title().equals(target.description()))
                .findFirst()
                .map(ChangeKey::encode);
    }

    private List<String> titlesOf(List<Change> changes) {
        return changes.stream().map(Change::title).toList();
    }

    private AiProvider requireAiProviderConfigured() {
        AiProvider provider = provider();
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI analysis is not configured: connect Claude or set the ANTHROPIC_API_KEY environment variable");
        }
        return provider;
    }

    private WebSession.SelectedPullRequest requireSelection() {
        session.gitHubToken()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub"));
        return session.selectedPullRequest()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No PR selected"));
    }
}
