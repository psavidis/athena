package com.athena.web.contextrewind;

import java.time.Instant;

/** One entry of an entity's evolution timeline, serialized for the frontend (ticket #187). */
public record TimelineEventResponse(String description, Instant occurredAt) {
}
