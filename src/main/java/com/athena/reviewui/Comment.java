package com.athena.reviewui;

/**
 * A reviewer's comment at some {@link AnnotationScope}. Comments are the
 * kind of annotation that syncs to GitHub — storage and the sync-exclusion
 * guarantee (which distinguishes this from {@link PrivateNote}) belong to
 * epic #6; this is the UI-facing shape epic #6's storage should mirror.
 */
public final class Comment {

    private final AnnotationScope scope;
    private final String text;

    Comment(AnnotationScope scope, String text) {
        this.scope = scope;
        this.text = text;
    }

    public AnnotationScope scope() {
        return scope;
    }

    public String text() {
        return text;
    }
}
