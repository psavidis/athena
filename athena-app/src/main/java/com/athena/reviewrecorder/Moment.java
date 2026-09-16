package com.athena.reviewrecorder;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One explicitly-tagged moment in a {@link ReviewRecording} (tickets
 * #205, #206): its kind, a reference to whichever entity/change was
 * active when it was tagged (the most recently captured
 * {@link SemanticEvent}'s own reference — {@code null} if none had been
 * captured yet), when it was tagged, and its confirmation status.
 *
 * <p>Immutable per this codebase's design principles: {@link #confirmed},
 * {@link #rejected}, and {@link #withKind} each return a new instance
 * rather than mutating this one, the same pattern {@code Participant}
 * already uses for its own state transitions. A moment starts
 * {@link MomentStatus#PENDING} and only a pending moment can transition —
 * an already-resolved (confirmed/rejected) moment is final.
 */
public final class Moment {

    private final String id;
    private final MomentKind kind;
    private final String reference;
    private final Instant taggedAt;
    private final MomentStatus status;

    private Moment(String id, MomentKind kind, String reference, Instant taggedAt, MomentStatus status) {
        this.id = id;
        this.kind = kind;
        this.reference = reference;
        this.taggedAt = taggedAt;
        this.status = status;
    }

    /** A brand-new, pending moment, tagged as {@code kind}. */
    static Moment tagged(MomentKind kind, String reference, Instant taggedAt) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(taggedAt, "taggedAt");
        return new Moment(UUID.randomUUID().toString(), kind, reference, taggedAt, MomentStatus.PENDING);
    }

    public String id() {
        return id;
    }

    public MomentKind kind() {
        return kind;
    }

    public String reference() {
        return reference;
    }

    public Instant taggedAt() {
        return taggedAt;
    }

    public MomentStatus status() {
        return status;
    }

    /** This moment, confirmed and made durable. Requires it still be pending. */
    Moment confirmed() {
        requirePending();
        return new Moment(id, kind, reference, taggedAt, MomentStatus.CONFIRMED);
    }

    /** This moment, rejected and discarded from any summary/timeline durability. Requires it still be pending. */
    Moment rejected() {
        requirePending();
        return new Moment(id, kind, reference, taggedAt, MomentStatus.REJECTED);
    }

    /** This moment with its kind changed to {@code newKind}, still pending. Requires it still be pending. */
    Moment withKind(MomentKind newKind) {
        requirePending();
        Objects.requireNonNull(newKind, "newKind");
        return new Moment(id, newKind, reference, taggedAt, MomentStatus.PENDING);
    }

    private void requirePending() {
        if (status != MomentStatus.PENDING) {
            throw new IllegalStateException("Moment " + id + " is already " + status);
        }
    }
}
