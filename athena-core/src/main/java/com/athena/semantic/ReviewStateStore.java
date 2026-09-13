package com.athena.semantic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An in-memory store of review state per Change, keyed by {@link ChangeIdentity}
 * rather than object identity — so state set on a Change survives it being
 * recomputed (e.g. re-running detection) as long as it's still the same
 * conceptual Change. Persistence/storage is explicitly out of scope for
 * epic #4's MVP (see ticket #21's Limitations of Scope).
 */
public final class ReviewStateStore {

    private final Map<ChangeIdentity, ReviewState> states = new HashMap<>();
    private final Set<ChangeIdentity> markedMechanical = new HashSet<>();

    /** Every newly-produced Change starts {@link ReviewState#UNSEEN} until set otherwise. */
    public ReviewState stateOf(Change change) {
        return states.getOrDefault(ChangeIdentity.of(change), ReviewState.UNSEEN);
    }

    /** Transitions a Change's review state. Unrestricted: any state may follow any other. */
    public void setState(Change change, ReviewState state) {
        states.put(ChangeIdentity.of(change), state);
    }

    /**
     * Marks a Change mechanical — a distinct action from setting its review
     * state to Reviewed. Preserves the distinction between "I inspected
     * everything" (Reviewed) and "I understood this as a mechanical
     * transformation" (mechanical), per epic #4 §19.
     */
    public void markMechanical(Change change) {
        markedMechanical.add(ChangeIdentity.of(change));
    }

    public boolean isMechanical(Change change) {
        return markedMechanical.contains(ChangeIdentity.of(change));
    }

    /**
     * Whether this Change counts as "accounted for" in review coverage. A Change flagged
     * {@link ReviewState#CONCERN} counts — the reviewer inspected it and formed a judgment,
     * even a negative one; coverage measures "did a human look at this," not "is every
     * concern resolved" (that distinction stays visible separately, e.g. via a Review
     * Context's own concern list).
     */
    boolean isAccountedFor(Change change) {
        ReviewState state = stateOf(change);
        return state == ReviewState.REVIEWED || state == ReviewState.SKIPPED
                || state == ReviewState.CONCERN || isMechanical(change);
    }
}
