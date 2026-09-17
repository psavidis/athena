package com.athena.reviewreplay;

import com.athena.reviewrecorder.Moment;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One item within a {@link ReviewOutcome} section (ticket #215): traces
 * back to the confirmed {@link Moment} it was derived from, so a
 * developer (or a later promotion into project memory, ticket #216) can
 * see exactly what evidence supports it — the same traceability
 * {@code com.athena.reviewbriefing.BriefingItem} already gives a Review
 * Briefing item (#218), here grounded in a moment's reference/tagged-at
 * instead of an AI-generated description.
 */
public final class OutcomeItem {

    private final String reference;
    private final Instant taggedAt;

    private OutcomeItem(String reference, Instant taggedAt) {
        this.reference = reference;
        this.taggedAt = taggedAt;
    }

    static OutcomeItem from(Moment moment) {
        Objects.requireNonNull(moment, "moment");
        Objects.requireNonNull(moment.taggedAt(), "moment.taggedAt");
        return new OutcomeItem(moment.reference(), moment.taggedAt());
    }

    /** The entity/change this item concerns, if its source moment had one. */
    public Optional<String> reference() {
        return Optional.ofNullable(reference);
    }

    public Instant taggedAt() {
        return taggedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OutcomeItem other)) return false;
        return Objects.equals(reference, other.reference) && taggedAt.equals(other.taggedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference, taggedAt);
    }

    @Override
    public String toString() {
        return "OutcomeItem[" + reference + " @ " + taggedAt + "]";
    }
}
