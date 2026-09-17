package com.athena.web.reviewreplay;

import com.athena.reviewreplay.ResolvedReference;

/**
 * A read-only view of one {@link ResolvedReference} (ticket #210, module
 * added in #212). {@code resolvedLabel}/{@code module} are plain nullable
 * fields rather than {@code Optional} (CODE_STYLE.md &sect;D.1), since
 * this is the JSON wire shape — {@code null} means the reference no
 * longer resolves, or its module couldn't be determined, respectively.
 */
public record ResolvedReferenceResponse(String reference, boolean resolved, String resolvedLabel, String module) {

    static ResolvedReferenceResponse of(ResolvedReference resolvedReference) {
        return new ResolvedReferenceResponse(resolvedReference.reference(), resolvedReference.resolved(),
                resolvedReference.resolvedLabel().orElse(null), resolvedReference.module().orElse(null));
    }
}
