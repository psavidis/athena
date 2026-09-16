package com.athena.livesession;

import com.athena.reviewui.AnnotationBoard;
import com.athena.reviewui.AnnotationScope;
import com.athena.reviewui.Comment;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A Live Code Review Session (ticket #158): lets multiple reviewers join
 * the same existing Athena review — one already-selected Pull Request —
 * and collaboratively navigate its Semantic Canvas together in real time.
 *
 * <p>This is the one cross-browser-session-shared aggregate the feature
 * needs. Everything else in the web layer ({@code WebSession}) is
 * deliberately per-browser-session; this class is the opposite by design,
 * held by {@link LiveReviewSessionRegistry} (an application-scoped
 * singleton) so a session created from one browser can be joined by ID
 * from a different one. It never re-runs review analysis or touches
 * {@code WebSession}/{@code Diff} at all — it only remembers which PR the
 * session is about (for display) and owns its own dedicated
 * {@link AnnotationBoard} for the collaborative comments participants add
 * during the session, kept separate from each participant's own personal
 * per-selection annotation board so a private note never becomes
 * visible to anyone else just by starting or joining a session.
 *
 * <p>Every mutating method is {@code synchronized}: concurrent requests
 * from different participants are serialized in arrival order, which is
 * this session's whole answer to "deterministic behavior when multiple
 * participants attempt to change shared state simultaneously" — whichever
 * request reaches the method first wins, no client-side merge required.
 * Each one ends by bumping its revision counter and notifying every
 * registered listener that something changed — a transport-agnostic seam
 * {@code com.athena.web.livesession.LiveReviewSessionController} wires an
 * SSE emitter into, reading this aggregate's own state back out through its
 * read accessors ({@link #participants()}, {@link #sharedFocus()}, etc.)
 * rather than this class building any wire-shaped payload itself — building
 * one is the web layer's job, keeping this domain class free of any
 * JSON/HTTP concern.
 */
public final class LiveReviewSession {

    private final String id;
    private final String repositoryFullName;
    private final int pullRequestNumber;
    private final Instant createdAt;
    private final String creatorId;
    private final AnnotationBoard comments = new AnnotationBoard();
    private final Map<String, Participant> participants = new LinkedHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private CanvasFocus sharedFocus = CanvasFocus.initial();
    private String presenterId;
    private long revision;
    private boolean ended;

    private LiveReviewSession(String repositoryFullName, int pullRequestNumber, String creatorDisplayName) {
        this.id = UUID.randomUUID().toString();
        this.repositoryFullName = repositoryFullName;
        this.pullRequestNumber = pullRequestNumber;
        this.createdAt = Instant.now();
        Participant creator = Participant.join(creatorDisplayName).asPresenting();
        this.creatorId = creator.id();
        this.presenterId = creator.id();
        this.participants.put(creator.id(), creator);
    }

    /** Starts a new session about {@code repositoryFullName}#{@code pullRequestNumber}, joining its creator as first participant/presenter. */
    static LiveReviewSession create(String repositoryFullName, int pullRequestNumber, String creatorDisplayName) {
        Objects.requireNonNull(repositoryFullName, "repositoryFullName");
        if (repositoryFullName.isBlank()) {
            throw new IllegalArgumentException("repositoryFullName must not be blank");
        }
        return new LiveReviewSession(repositoryFullName, pullRequestNumber, creatorDisplayName);
    }

    public String id() {
        return id;
    }

    public String repositoryFullName() {
        return repositoryFullName;
    }

    public int pullRequestNumber() {
        return pullRequestNumber;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String creatorId() {
        return creatorId;
    }

    public Optional<String> presenterId() {
        return Optional.ofNullable(presenterId);
    }

    public boolean ended() {
        return ended;
    }

    public CanvasFocus sharedFocus() {
        return sharedFocus;
    }

    /** Every participant, in the order they joined. */
    public List<Participant> participants() {
        return List.copyOf(participants.values());
    }

    /** Increases by one on every state change — see this class's own doc for what that's used for. */
    public long revision() {
        return revision;
    }

    /**
     * Joins {@code displayName} to the session. When {@code existingParticipantId} names a
     * participant already known to this session (a reconnect after a dropped connection),
     * that participant is marked connected again and keeps their mode/focus/presenter status
     * instead of starting over — otherwise (first join, or an id this session doesn't
     * recognize) a brand-new participant is created.
     */
    public synchronized Participant join(Optional<String> existingParticipantId, String displayName) {
        Optional<Participant> reconnecting = existingParticipantId.map(participants::get);
        Participant participant = reconnecting
                .map(existing -> existing.withConnected(true).withDisplayName(displayName))
                .orElseGet(() -> Participant.join(displayName));
        participants.put(participant.id(), participant);
        broadcast();
        return participant;
    }

    /** Removes {@code participantId} entirely — an explicit "leave," not a transient disconnect. */
    public synchronized void leave(String participantId) {
        requireParticipant(participantId);
        participants.remove(participantId);
        if (participantId.equals(presenterId)) {
            presenterId = null;
        }
        broadcast();
    }

    /** Marks {@code participantId} as no longer connected (a dropped SSE connection) without discarding their state. */
    public synchronized void disconnect(String participantId) {
        Participant participant = participants.get(participantId);
        if (participant == null) {
            return;
        }
        participants.put(participantId, participant.withConnected(false));
        broadcast();
    }

    /** {@code participantId} takes control of the session's shared navigation. */
    public synchronized void takeControl(String participantId) {
        Participant participant = requireParticipant(participantId);
        if (presenterId != null && !presenterId.equals(participantId)) {
            Participant oldPresenter = participants.get(presenterId);
            if (oldPresenter != null) {
                participants.put(oldPresenter.id(), oldPresenter.asFollowing());
            }
        }
        presenterId = participantId;
        participants.put(participantId, participant.asPresenting());
        broadcast();
    }

    /**
     * {@code participantId} moves the session's shared focus — only valid for the current
     * presenter; every following participant's view is meant to track this.
     */
    public synchronized void presentFocus(String participantId, CanvasFocus focus) {
        requireParticipant(participantId);
        if (!participantId.equals(presenterId)) {
            throw new IllegalStateException("Only the current presenter can move the shared focus");
        }
        this.sharedFocus = Objects.requireNonNull(focus, "focus");
        broadcast();
    }

    /** {@code participantId} stops presenting/exploring and returns to following the shared focus. */
    public synchronized void follow(String participantId) {
        Participant participant = requireParticipant(participantId);
        if (participantId.equals(presenterId)) {
            presenterId = null;
        }
        participants.put(participantId, participant.asFollowing());
        broadcast();
    }

    /**
     * {@code participantId} starts (or continues) exploring the canvas independently, at
     * {@code focus} — visible to other participants as "where they're looking," but never
     * affecting the shared focus itself.
     */
    public synchronized void explore(String participantId, CanvasFocus focus) {
        Participant participant = requireParticipant(participantId);
        if (participantId.equals(presenterId)) {
            presenterId = null;
        }
        participants.put(participantId, participant.asExploring(focus));
        broadcast();
    }

    /** Adds a comment attributed to {@code participantId}'s display name, visible to every participant. */
    public synchronized void addComment(String participantId, AnnotationScope scope, String text) {
        Participant participant = requireParticipant(participantId);
        comments.addComment(scope, participant.displayName(), text);
        broadcast();
    }

    /** Every comment posted at {@code scope} during this session, oldest first. */
    public List<Comment> commentsAt(AnnotationScope scope) {
        return comments.commentsAt(scope);
    }

    /** Ends the session — only the participant who created it may do this. */
    public synchronized void end(String participantId) {
        requireParticipant(participantId);
        if (!participantId.equals(creatorId)) {
            throw new IllegalStateException("Only the session's creator can end it");
        }
        this.ended = true;
        broadcast();
    }

    /** Registers a listener notified (with no payload — read this session's own state back via its accessors) on every state change, until {@link #removeListener}. */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private Participant requireParticipant(String participantId) {
        Participant participant = participants.get(participantId);
        if (participant == null) {
            throw new NoSuchElementException("No participant " + participantId + " in this session");
        }
        return participant;
    }

    private void broadcast() {
        revision++;
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
