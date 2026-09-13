package com.athena.reviewui;

/**
 * A reviewer's private note at some {@link AnnotationScope} — a distinct
 * type from {@link Comment}, not a flag on a shared type, so it is
 * visually and structurally impossible to confuse the two (epic #5 §22).
 * Private notes must never sync to GitHub; that guarantee is enforced by
 * epic #6's storage/sync layer, not here — this ticket is UI-only.
 */
public final class PrivateNote {

    private final AnnotationScope scope;
    private final String text;

    PrivateNote(AnnotationScope scope, String text) {
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
