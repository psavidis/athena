package com.athena.livesession;

/**
 * A participant's relationship to a {@link LiveReviewSession}'s shared
 * navigation (ticket #158) — the "shared navigation vs. personal
 * exploration" distinction the ticket calls out as the central UX
 * decision. Plain data tags, not a Strategy Enum (CODE_STYLE.md &sect;A.2):
 * no behavior varies per value, {@link LiveReviewSession} itself decides
 * what each mode means.
 */
public enum ParticipantMode {

    /** Consuming the session's shared focus — this participant's view tracks the presenter's. */
    FOLLOWING,

    /** Navigating independently; {@link Participant#personalFocus()} tracks where, without touching the shared focus. */
    EXPLORING,

    /** Currently in control of the session's shared focus (see {@link LiveReviewSession#presenterId()}). */
    PRESENTING
}
