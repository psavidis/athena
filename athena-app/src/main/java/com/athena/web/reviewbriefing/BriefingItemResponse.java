package com.athena.web.reviewbriefing;

import com.athena.reviewbriefing.BriefingItem;

/**
 * A read-only view of one {@link BriefingItem} (ticket #223).
 * {@code entityReference} is a plain nullable field rather than {@code
 * Optional} (CODE_STYLE.md &sect;D.1), since this is the JSON wire shape.
 */
public record BriefingItemResponse(String description, String entityReference) {

    static BriefingItemResponse of(BriefingItem item) {
        return new BriefingItemResponse(item.description(), item.entityReference().orElse(null));
    }
}
