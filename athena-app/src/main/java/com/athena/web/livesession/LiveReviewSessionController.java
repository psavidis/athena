package com.athena.web.livesession;

import com.athena.livesession.CanvasFocus;
import com.athena.livesession.LiveReviewSession;
import com.athena.livesession.LiveReviewSessionRegistry;
import com.athena.livesession.LiveReviewSessionSnapshot;
import com.athena.livesession.Participant;
import com.athena.reviewui.AnnotationScope;
import com.athena.web.WebSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The web entry point for a Live Code Review Session (ticket #158):
 * starting one from the caller's current review, joining one by id
 * (including reconnecting), shared-navigation actions (take control,
 * present, follow, explore), collaborative comments, and the SSE stream
 * every connected participant's browser subscribes to for real-time
 * updates.
 *
 * <p>Only {@link #create} consults {@link WebSession} (to read the
 * caller's currently-selected PR) — every other action is looked up purely
 * by session id + the participant id the caller sends back, deliberately
 * independent of {@code WebSession}'s per-browser selection: a Live Code
 * Review Session is the one thing in this application meant to be reached
 * the same way from any browser, per {@link LiveReviewSessionRegistry}'s
 * own class doc.
 */
@RestController
@RequestMapping("/api/live-sessions")
public class LiveReviewSessionController {

    private final WebSession session;
    private final LiveReviewSessionRegistry registry;

    public LiveReviewSessionController(WebSession session, LiveReviewSessionRegistry registry) {
        this.session = session;
        this.registry = registry;
    }

    @PostMapping
    public LiveSessionJoinResponse create(@RequestBody CreateLiveSessionRequest request) {
        WebSession.SelectedPullRequest selected = session.selectedPullRequest()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Select a Pull Request before starting a Live Code Review Session"));
        String displayName = requireDisplayName(request.displayName());
        LiveReviewSession liveSession = registry.create(
                selected.repositoryFullName(), selected.pullRequest().number(), displayName);
        return new LiveSessionJoinResponse(liveSession.id(), liveSession.creatorId(), liveSession.snapshot());
    }

    @PostMapping("/{id}/join")
    public LiveSessionJoinResponse join(@PathVariable String id, @RequestBody JoinLiveSessionRequest request) {
        LiveReviewSession liveSession = requireSession(id);
        String displayName = requireDisplayName(request.displayName());
        Participant participant = liveSession.join(Optional.ofNullable(request.participantId()), displayName);
        return new LiveSessionJoinResponse(id, participant.id(), liveSession.snapshot());
    }

    @GetMapping("/{id}")
    public LiveReviewSessionSnapshot snapshot(@PathVariable String id) {
        return requireSession(id).snapshot();
    }

    @GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String id, @RequestParam String participantId) {
        LiveReviewSession liveSession = requireSession(id);
        SseEmitter emitter = new SseEmitter(0L);
        Consumer<LiveReviewSessionSnapshot> listener = snapshotEvent -> sendSnapshot(emitter, snapshotEvent);
        liveSession.addListener(listener);
        Runnable onDisconnect = () -> {
            liveSession.removeListener(listener);
            liveSession.disconnect(participantId);
        };
        emitter.onCompletion(onDisconnect);
        emitter.onTimeout(onDisconnect);
        emitter.onError(e -> onDisconnect.run());
        sendSnapshot(emitter, liveSession.snapshot());
        return emitter;
    }

    private void sendSnapshot(SseEmitter emitter, LiveReviewSessionSnapshot snapshotEvent) {
        try {
            emitter.send(SseEmitter.event().name("snapshot").data(snapshotEvent));
        } catch (IOException e) {
            // Genuine external boundary: the client's HTTP connection may already be gone by
            // the time we push to it — that is a normal disconnect, not a bug, so we complete
            // the emitter (triggering onCompletion's cleanup) rather than propagating.
            emitter.completeWithError(e);
        }
    }

    @PostMapping("/{id}/take-control")
    public LiveReviewSessionSnapshot takeControl(@PathVariable String id, @RequestBody LiveParticipantRequest request) {
        LiveReviewSession liveSession = requireSession(id);
        withParticipant(() -> liveSession.takeControl(request.participantId()));
        return liveSession.snapshot();
    }

    @PostMapping("/{id}/follow")
    public LiveReviewSessionSnapshot follow(@PathVariable String id, @RequestBody LiveParticipantRequest request) {
        LiveReviewSession liveSession = requireSession(id);
        withParticipant(() -> liveSession.follow(request.participantId()));
        return liveSession.snapshot();
    }

    @PostMapping("/{id}/explore")
    public LiveReviewSessionSnapshot explore(@PathVariable String id, @RequestBody LiveFocusRequest request) {
        LiveReviewSession liveSession = requireSession(id);
        CanvasFocus focus = requireFocus(request);
        withParticipant(() -> liveSession.explore(request.participantId(), focus));
        return liveSession.snapshot();
    }

    @PostMapping("/{id}/focus")
    public LiveReviewSessionSnapshot presentFocus(@PathVariable String id, @RequestBody LiveFocusRequest request) {
        LiveReviewSession liveSession = requireSession(id);
        CanvasFocus focus = requireFocus(request);
        try {
            liveSession.presentFocus(request.participantId(), focus);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        return liveSession.snapshot();
    }

    @PostMapping("/{id}/leave")
    public LiveReviewSessionSnapshot leave(@PathVariable String id, @RequestBody LiveParticipantRequest request) {
        LiveReviewSession liveSession = requireSession(id);
        withParticipant(() -> liveSession.leave(request.participantId()));
        return liveSession.snapshot();
    }

    @PostMapping("/{id}/end")
    public LiveReviewSessionSnapshot end(@PathVariable String id, @RequestBody LiveParticipantRequest request) {
        LiveReviewSession liveSession = requireSession(id);
        try {
            liveSession.end(request.participantId());
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        registry.remove(id);
        return liveSession.snapshot();
    }

    @PostMapping("/{id}/comments")
    public List<LiveCommentResponse> addComment(@PathVariable String id, @RequestBody LiveCommentRequest request) {
        LiveReviewSession liveSession = requireSession(id);
        AnnotationScope scope = scopeFor(request.canvasItemId());
        try {
            liveSession.addComment(request.participantId(), scope, request.text());
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text must not be blank");
        }
        return commentsAt(liveSession, scope);
    }

    @GetMapping("/{id}/comments")
    public List<LiveCommentResponse> comments(@PathVariable String id,
                                               @RequestParam(required = false) String canvasItemId) {
        LiveReviewSession liveSession = requireSession(id);
        return commentsAt(liveSession, scopeFor(canvasItemId));
    }

    private List<LiveCommentResponse> commentsAt(LiveReviewSession liveSession, AnnotationScope scope) {
        return liveSession.commentsAt(scope).stream().map(LiveCommentResponse::of).toList();
    }

    private AnnotationScope scopeFor(String canvasItemId) {
        return (canvasItemId == null || canvasItemId.isBlank())
                ? AnnotationScope.review()
                : AnnotationScope.canvasItem(canvasItemId);
    }

    private CanvasFocus requireFocus(LiveFocusRequest request) {
        try {
            return CanvasFocus.of(request.zoomLevel(), request.selectedEntityId(), request.selectedChangeKey(),
                    request.navigationContext());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "zoomLevel must not be blank");
        }
    }

    private void withParticipant(Runnable action) {
        try {
            action.run();
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private String requireDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName must not be blank");
        }
        return displayName;
    }

    private LiveReviewSession requireSession(String id) {
        return registry.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such Live Code Review Session"));
    }
}
