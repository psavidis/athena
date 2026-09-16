package com.athena.web.reviewrecorder;

import com.athena.reviewrecorder.ReviewRecordingSummary;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * A read-only view of a {@link ReviewRecordingSummary} (ticket #206):
 * duration in seconds, and confirmed-moment counts keyed by the moment
 * kind's plain name (a wire-friendly {@code Map<String, Integer>} rather
 * than keying by the enum itself, per CODE_STYLE.md &sect;D.1).
 */
public record ReviewRecordingSummaryResponse(long durationSeconds, Map<String, Integer> momentCountsByKind) {

    static ReviewRecordingSummaryResponse of(ReviewRecordingSummary summary) {
        Map<String, Integer> counts = summary.momentCountsByKind().entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue));
        return new ReviewRecordingSummaryResponse(summary.duration().getSeconds(), counts);
    }
}
