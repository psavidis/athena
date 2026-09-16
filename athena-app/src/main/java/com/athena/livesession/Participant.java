package com.athena.livesession;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One reviewer connected to a {@link LiveReviewSession} (ticket #158).
 * Immutable per this codebase's design principles: every state
 * transition ({@link #withConnected}, {@link #withMode}) returns a new
 * instance rather than mutating this one, the same "replace at this id"
 * pattern {@code AnnotationBoard} already uses for {@code Comment}.
 */
public final class Participant {

    private final String id;
    private final String displayName;
    private final boolean connected;
    private final ParticipantMode mode;
    private final CanvasFocus personalFocus;
    private final Instant joinedAt;

    private Participant(String id, String displayName, boolean connected, ParticipantMode mode,
                         CanvasFocus personalFocus, Instant joinedAt) {
        this.id = id;
        this.displayName = displayName;
        this.connected = connected;
        this.mode = mode;
        this.personalFocus = personalFocus;
        this.joinedAt = joinedAt;
    }

    /** A brand-new participant joining a session, in {@link ParticipantMode#FOLLOWING} mode. */
    static Participant join(String displayName) {
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        return new Participant(UUID.randomUUID().toString(), displayName, true, ParticipantMode.FOLLOWING, null,
                Instant.now());
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean connected() {
        return connected;
    }

    public ParticipantMode mode() {
        return mode;
    }

    /** Where this participant is personally exploring, if {@link #mode()} is {@link ParticipantMode#EXPLORING}. */
    public Optional<CanvasFocus> personalFocus() {
        return Optional.ofNullable(personalFocus);
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    Participant withDisplayName(String newDisplayName) {
        return new Participant(id, newDisplayName, connected, mode, personalFocus, joinedAt);
    }

    Participant withConnected(boolean newConnected) {
        return new Participant(id, displayName, newConnected, mode, personalFocus, joinedAt);
    }

    /** Switches to {@link ParticipantMode#FOLLOWING}, clearing any personal exploration focus. */
    Participant asFollowing() {
        return new Participant(id, displayName, connected, ParticipantMode.FOLLOWING, null, joinedAt);
    }

    /** Switches to {@link ParticipantMode#EXPLORING} at {@code focus}. */
    Participant asExploring(CanvasFocus focus) {
        return new Participant(id, displayName, connected, ParticipantMode.EXPLORING,
                Objects.requireNonNull(focus, "focus"), joinedAt);
    }

    /** Switches to {@link ParticipantMode#PRESENTING}, clearing any personal exploration focus. */
    Participant asPresenting() {
        return new Participant(id, displayName, connected, ParticipantMode.PRESENTING, null, joinedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Participant other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
