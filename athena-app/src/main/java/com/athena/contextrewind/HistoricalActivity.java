package com.athena.contextrewind;

import java.time.Instant;

/**
 * One dated touch of an entity's history (ticket #161) — the unit
 * {@link ReconstructedContext#evolutionTimeline()} orders chronologically,
 * and {@link ContextRewindRequest#since()} filters for a "catch me up"
 * reconstruction.
 */
public record HistoricalActivity(String description, Instant occurredAt) {
}
