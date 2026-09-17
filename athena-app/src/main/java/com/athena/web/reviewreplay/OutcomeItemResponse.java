package com.athena.web.reviewreplay;

import com.athena.reviewreplay.OutcomeItem;

import java.time.Instant;

/**
 * A read-only view of one {@link OutcomeItem} (ticket #215). {@code
 * reference} is a plain nullable field rather than {@code Optional}
 * (CODE_STYLE.md &sect;D.1), since this is the JSON wire shape.
 */
public record OutcomeItemResponse(String reference, Instant taggedAt) {

    static OutcomeItemResponse of(OutcomeItem item) {
        return new OutcomeItemResponse(item.reference().orElse(null), item.taggedAt());
    }
}
