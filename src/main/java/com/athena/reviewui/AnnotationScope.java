package com.athena.reviewui;

import com.athena.semantic.Change;
import com.athena.semantic.ChangeIdentity;

import java.util.Objects;

/**
 * Where a comment or private note attaches: a specific line, a symbol, a
 * whole Change, or the review overall (epic #5 §21). Modeled as a value
 * type (not a bare enum) since line scope carries a file+line, and Change
 * scope carries the Change's stable {@link ChangeIdentity} — not the
 * {@link Change} instance itself, which has no equals/hashCode and would
 * make an annotation unreachable the moment the engine recomputes the same
 * conceptual Change into a new instance (e.g. after a force-push).
 */
public final class AnnotationScope {

    private enum Kind { LINE, SYMBOL, CHANGE, REVIEW }

    private final Kind kind;
    private final String filePath;
    private final int line;
    private final String symbolDescription;
    private final ChangeIdentity changeIdentity;

    private AnnotationScope(Kind kind, String filePath, int line, String symbolDescription, ChangeIdentity changeIdentity) {
        this.kind = kind;
        this.filePath = filePath;
        this.line = line;
        this.symbolDescription = symbolDescription;
        this.changeIdentity = changeIdentity;
    }

    public static AnnotationScope line(String filePath, int line) {
        Objects.requireNonNull(filePath, "filePath");
        if (filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be blank");
        }
        if (line <= 0) {
            throw new IllegalArgumentException("line must be positive: " + line);
        }
        return new AnnotationScope(Kind.LINE, filePath, line, null, null);
    }

    public static AnnotationScope symbol(String symbolDescription) {
        Objects.requireNonNull(symbolDescription, "symbolDescription");
        if (symbolDescription.isBlank()) {
            throw new IllegalArgumentException("symbolDescription must not be blank");
        }
        return new AnnotationScope(Kind.SYMBOL, null, 0, symbolDescription, null);
    }

    public static AnnotationScope change(Change change) {
        Objects.requireNonNull(change, "change");
        return new AnnotationScope(Kind.CHANGE, null, 0, null, ChangeIdentity.of(change));
    }

    public static AnnotationScope review() {
        return new AnnotationScope(Kind.REVIEW, null, 0, null, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnnotationScope other)) return false;
        return kind == other.kind && line == other.line
                && Objects.equals(filePath, other.filePath)
                && Objects.equals(symbolDescription, other.symbolDescription)
                && Objects.equals(changeIdentity, other.changeIdentity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, filePath, line, symbolDescription, changeIdentity);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case LINE -> filePath + ":" + line;
            case SYMBOL -> symbolDescription;
            case CHANGE -> "Change[" + changeIdentity + "]";
            case REVIEW -> "review";
        };
    }
}
