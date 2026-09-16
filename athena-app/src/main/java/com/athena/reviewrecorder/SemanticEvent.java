package com.athena.reviewrecorder;

import java.time.Instant;
import java.util.Objects;

/**
 * One captured semantic event in a {@link ReviewRecording}'s event stream
 * (ticket #204): its type, a reference to the entity/file/change it
 * concerns (an opaque id — this class relays it, never interprets it,
 * matching {@code CanvasFocus}'s own precedent for staying agnostic of
 * the canvas's id scheme), and when it occurred.
 */
public final class SemanticEvent {

    private final SemanticEventType type;
    private final String reference;
    private final Instant occurredAt;

    private SemanticEvent(SemanticEventType type, String reference, Instant occurredAt) {
        this.type = type;
        this.reference = reference;
        this.occurredAt = occurredAt;
    }

    public static SemanticEvent of(SemanticEventType type, String reference, Instant occurredAt) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (reference.isBlank()) {
            throw new IllegalArgumentException("reference must not be blank");
        }
        return new SemanticEvent(type, reference, occurredAt);
    }

    public SemanticEventType type() {
        return type;
    }

    public String reference() {
        return reference;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
