package com.athena.reviewreplay;

import com.athena.memory.MemoryEntry;

import java.util.Objects;
import java.util.Optional;

/**
 * Promotes one {@link OutcomeItem} from a {@link ReviewReplay}'s {@link
 * ReviewOutcome} into a {@link MemoryEntry} (ticket #216) — always an
 * explicit developer action (never invoked automatically), preserving
 * the item's origin (repository, PR, and the source moment's reference)
 * as the entry's evidence, per the ticket's "review → semantic events →
 * evidence" requirement. {@code fact} is the developer's own words for
 * what was learned/decided — {@link OutcomeItem} intentionally carries no
 * generated description (#215) for a promotion to reuse.
 */
public final class OutcomePromotion {

    private OutcomePromotion() {
    }

    /** Builds the {@link MemoryEntry} to record for promoting {@code item} from {@code replay}, if it exists. */
    public static Optional<MemoryEntry> entryFor(ReviewReplay replay, OutcomeSection section, String reference, String fact) {
        Objects.requireNonNull(replay, "replay");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(fact, "fact");
        return replay.outcome().item(section, reference).map(item -> new MemoryEntry(fact, evidenceFor(replay, item), "high", true));
    }

    private static String evidenceFor(ReviewReplay replay, OutcomeItem item) {
        String base = "Review of " + replay.repositoryFullName() + " PR " + replay.pullRequestNumber();
        return item.reference().map(ref -> base + ", " + ref).orElse(base);
    }
}
