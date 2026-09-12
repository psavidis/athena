package com.athena.web;

import com.athena.ai.AiAnalysisOrchestrator;
import com.athena.ai.AiContextBoundary;
import com.athena.ai.AiFindingItem;
import com.athena.ai.AiFindingsBoard;
import com.athena.ai.AiProvider;
import com.athena.ai.ClaudeAiProvider;
import com.athena.reviewui.ChangeDetailView;
import com.athena.semantic.Change;
import com.athena.semantic.ChangeGrouper;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.TransformationDetector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
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
    private final AiProvider provider;

    @Autowired
    public AiAnalysisController(WebSession session) {
        this(session, new ClaudeAiProvider(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                System.getenv("ANTHROPIC_API_KEY")));
    }

    /** Test seam: a fake {@link AiProvider} stands in for the real network boundary. */
    AiAnalysisController(WebSession session, AiProvider provider) {
        this.session = session;
        this.provider = provider;
    }

    @GetMapping("/api/review/ai-context-boundary")
    public AiContextBoundaryResponse contextBoundary() {
        WebSession.SelectedPullRequest selection = requireSelection();
        AiContextBoundary boundary = AiContextBoundary.assemble(selection.pullRequest().title(),
                detectChanges(selection), selection.reviewStateStore(), selection.annotationBoard());

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
        WebSession.SelectedPullRequest selection = requireSelection();
        List<Change> changes = detectChanges(selection);

        AiFindingsBoard board = AiAnalysisOrchestrator.trigger(selection.pullRequest().title(), changes,
                selection.reviewStateStore(), selection.annotationBoard(), provider);
        session.setAiFindingsBoard(board);

        return findingResponses(board, changes);
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
        return findingResponses(board, detectChanges(selection));
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

    private WebSession.SelectedPullRequest requireSelection() {
        session.gitHubToken()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not connected to GitHub"));
        return session.selectedPullRequest()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No PR selected"));
    }

    private List<Change> detectChanges(WebSession.SelectedPullRequest selection) {
        List<DetectedTransformation> transformations =
                new TransformationDetector().detect(selection.baseRoot(), selection.headRoot());
        return new ChangeGrouper().group(transformations);
    }
}
