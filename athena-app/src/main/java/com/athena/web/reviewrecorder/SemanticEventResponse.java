package com.athena.web.reviewrecorder;

import com.athena.reviewrecorder.SemanticEvent;

import java.time.Instant;

/** A read-only view of one captured {@link SemanticEvent} (ticket #204). */
public record SemanticEventResponse(String type, String reference, Instant occurredAt) {

    static SemanticEventResponse of(SemanticEvent event) {
        return new SemanticEventResponse(event.type().name(), event.reference(), event.occurredAt());
    }
}
