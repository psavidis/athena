package com.athena.git;

import java.time.Duration;

/**
 * How long a revision fetch may take and how often a transient failure is retried (ticket #359).
 * {@code lowSpeedBytesPerSecond} for {@code lowSpeedTime} is git's own abort for a transfer that
 * stays too slow; {@code timeout} bounds each whole fetch process as a backstop, e.g. for a
 * connection that never starts sending. Immutable.
 */
public record FetchLimits(int lowSpeedBytesPerSecond, Duration lowSpeedTime, Duration timeout, int retries) {

    /** Abort below 1 KB/s for 60 s, give up on a fetch after 10 minutes, retry a transient failure once. */
    public static final FetchLimits DEFAULT = new FetchLimits(1000, Duration.ofSeconds(60), Duration.ofMinutes(10), 1);

    public FetchLimits withTimeout(Duration timeout) {
        return new FetchLimits(lowSpeedBytesPerSecond, lowSpeedTime, timeout, retries);
    }

    public FetchLimits withRetries(int retries) {
        return new FetchLimits(lowSpeedBytesPerSecond, lowSpeedTime, timeout, retries);
    }
}
