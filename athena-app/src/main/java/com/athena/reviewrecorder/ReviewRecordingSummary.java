package com.athena.reviewrecorder;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * A concise summary of a {@link ReviewRecording} (ticket #206): its
 * duration and confirmed-moment counts by kind. A pending or rejected
 * moment is excluded from the counts — only what a human explicitly
 * confirmed is durable enough to summarize.
 */
public final class ReviewRecordingSummary {

    private final Duration duration;
    private final Map<MomentKind, Integer> momentCountsByKind;

    private ReviewRecordingSummary(Duration duration, Map<MomentKind, Integer> momentCountsByKind) {
        this.duration = duration;
        this.momentCountsByKind = momentCountsByKind;
    }

    static ReviewRecordingSummary of(Duration duration, java.util.List<Moment> moments) {
        Objects.requireNonNull(duration, "duration");
        Map<MomentKind, Integer> counts = new EnumMap<>(MomentKind.class);
        for (Moment moment : moments) {
            if (moment.status() == MomentStatus.CONFIRMED) {
                counts.merge(moment.kind(), 1, Integer::sum);
            }
        }
        return new ReviewRecordingSummary(duration, counts);
    }

    public Duration duration() {
        return duration;
    }

    /** Confirmed-moment counts by kind. A kind with no confirmed moments is absent, not zero. */
    public Map<MomentKind, Integer> momentCountsByKind() {
        return Map.copyOf(momentCountsByKind);
    }
}
