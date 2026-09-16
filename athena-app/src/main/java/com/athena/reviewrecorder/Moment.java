package com.athena.reviewrecorder;

import java.time.Instant;
import java.util.Objects;

/**
 * One explicitly-tagged moment in a {@link ReviewRecording} (ticket
 * #205): its kind, a reference to whichever entity/change was active
 * when it was tagged (the most recently captured {@link SemanticEvent}'s
 * own reference — {@code null} if none had been captured yet, since a
 * moment can still be tagged with nothing to reference), and when it was
 * tagged.
 */
public final class Moment {

    private final MomentKind kind;
    private final String reference;
    private final Instant taggedAt;

    private Moment(MomentKind kind, String reference, Instant taggedAt) {
        this.kind = kind;
        this.reference = reference;
        this.taggedAt = taggedAt;
    }

    public static Moment of(MomentKind kind, String reference, Instant taggedAt) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(taggedAt, "taggedAt");
        return new Moment(kind, reference, taggedAt);
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
}
