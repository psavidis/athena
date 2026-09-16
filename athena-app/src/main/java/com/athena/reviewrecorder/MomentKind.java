package com.athena.reviewrecorder;

/**
 * The kinds of moment a developer can explicitly tag during a Review
 * Recording (ticket #205): insight, question, concern, decision, action,
 * verification. Explicit human actions only — inference from
 * audio/transcript is a later capability's concern, not this enum's.
 */
public enum MomentKind {
    INSIGHT,
    QUESTION,
    CONCERN,
    DECISION,
    ACTION,
    VERIFICATION
}
