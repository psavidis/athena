package com.athena.web.reviewrecorder;

import com.athena.reviewrecorder.Moment;

import java.time.Instant;

/**
 * A read-only view of one tagged {@link Moment} (tickets #205, #206).
 * {@code reference} is a plain nullable field rather than {@code Optional}
 * (CODE_STYLE.md &sect;D.1), since this is the JSON wire shape. {@code kind}
 * and {@code status} are plain strings naming their respective enum
 * values, for the same reason.
 */
public record MomentResponse(String momentId, String kind, String reference, Instant taggedAt, String status) {

    static MomentResponse of(Moment moment) {
        return new MomentResponse(moment.id(), moment.kind().name(), moment.reference(), moment.taggedAt(),
                moment.status().name());
    }
}
