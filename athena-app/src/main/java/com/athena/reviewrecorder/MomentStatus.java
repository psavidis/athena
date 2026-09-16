package com.athena.reviewrecorder;

/**
 * A {@link Moment}'s confirmation status (ticket #206): a tagged moment
 * starts {@code PENDING} and requires an explicit human confirmation
 * step — even for an explicit tag — before it becomes durable
 * ({@code CONFIRMED}), so a mis-tap can still be corrected
 * ({@code REJECTED}) rather than persisting.
 */
public enum MomentStatus {
    PENDING,
    CONFIRMED,
    REJECTED
}
