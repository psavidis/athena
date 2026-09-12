package com.athena.semantic;

/**
 * The explicit review state of a Change (epic #4 §18). A newly-produced
 * Change starts {@link #UNSEEN}; transitions between any two states are
 * unrestricted — the spec calls for explicit states, not a restricted
 * state machine.
 */
public enum ReviewState {
    UNSEEN,
    UNDERSTANDING,
    REVIEWED,
    CONCERN,
    SKIPPED
}
