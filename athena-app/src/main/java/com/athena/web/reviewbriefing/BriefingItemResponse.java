package com.athena.web.reviewbriefing;

import com.athena.reviewbriefing.BriefingItem;
import com.athena.reviewreplay.EntityModuleResolver;
import com.athena.semantic.Change;
import com.athena.semantic.ModuleGrouper;

import java.util.List;

/**
 * A read-only view of one {@link BriefingItem} (ticket #223), with its
 * entity reference resolved to a Semantic Canvas module (ticket #224) via
 * the same {@link EntityModuleResolver} Review Replay's canvas
 * integration (#212) already established. {@code entityReference}/{@code
 * module} are plain nullable fields rather than {@code Optional}
 * (CODE_STYLE.md &sect;D.1), since this is the JSON wire shape.
 */
public record BriefingItemResponse(String description, String entityReference, String module) {

    static BriefingItemResponse of(BriefingItem item, List<Change> changes, ModuleGrouper grouper) {
        String module = item.entityReference()
                .flatMap(entityReference -> EntityModuleResolver.resolve(entityReference, changes, grouper))
                .orElse(null);
        return new BriefingItemResponse(item.description(), item.entityReference().orElse(null), module);
    }
}
