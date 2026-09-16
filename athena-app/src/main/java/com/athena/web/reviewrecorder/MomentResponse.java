package com.athena.web.reviewrecorder;

import com.athena.reviewrecorder.Moment;

import java.time.Instant;

/**
 * A read-only view of one tagged {@link Moment} (ticket #205).
 * {@code reference} is a plain nullable field rather than {@code Optional}
 * (CODE_STYLE.md &sect;D.1), since this is the JSON wire shape.
 */
public record MomentResponse(String kind, String reference, Instant taggedAt) {

    static MomentResponse of(Moment moment) {
        return new MomentResponse(moment.kind().name(), moment.reference(), moment.taggedAt());
    }
}
