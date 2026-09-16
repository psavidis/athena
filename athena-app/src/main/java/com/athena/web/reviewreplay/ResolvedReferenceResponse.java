package com.athena.web.reviewreplay;

import com.athena.reviewreplay.ResolvedReference;

/**
 * A read-only view of one {@link ResolvedReference} (ticket #210).
 * {@code resolvedLabel} is a plain nullable field rather than {@code
 * Optional} (CODE_STYLE.md &sect;D.1), since this is the JSON wire shape —
 * {@code null} means the reference no longer resolves.
 */
public record ResolvedReferenceResponse(String reference, boolean resolved, String resolvedLabel) {

    static ResolvedReferenceResponse of(ResolvedReference resolvedReference) {
        return new ResolvedReferenceResponse(resolvedReference.reference(), resolvedReference.resolved(),
                resolvedReference.resolvedLabel().orElse(null));
    }
}
