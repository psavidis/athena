package com.athena.reviewui;

import com.athena.semantic.Change;
import com.athena.semantic.ChangeIdentity;

import java.util.Objects;
import java.util.Optional;

/**
 * Where a comment or private note attaches: a specific line, a symbol, a
 * whole Change, a Semantic Canvas item (ticket #134), or the review overall
 * (epic #5 §21). Modeled as a value type (not a bare enum) since line scope
 * carries a file+line, Change scope carries the Change's stable
 * {@link ChangeIdentity} — not the {@link Change} instance itself, which has
 * no equals/hashCode and would make an annotation unreachable the moment the
 * engine recomputes the same conceptual Change into a new instance (e.g.
 * after a force-push) — and canvas-item scope carries the canvas's own
 * moduleName-scoped item id.
 */
public final class AnnotationScope {

    private enum Kind { LINE, SYMBOL, CHANGE, REVIEW, CANVAS_ITEM }

    private final Kind kind;
    private final String filePath;
    private final int line;
    private final String symbolDescription;
    private final ChangeIdentity changeIdentity;
    private final String canvasItemId;

    private AnnotationScope(Kind kind, String filePath, int line, String symbolDescription,
                             ChangeIdentity changeIdentity, String canvasItemId) {
        this.kind = kind;
        this.filePath = filePath;
        this.line = line;
        this.symbolDescription = symbolDescription;
        this.changeIdentity = changeIdentity;
        this.canvasItemId = canvasItemId;
    }

    public static AnnotationScope line(String filePath, int line) {
        Objects.requireNonNull(filePath, "filePath");
        if (filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be blank");
        }
        if (line <= 0) {
            throw new IllegalArgumentException("line must be positive: " + line);
        }
        return new AnnotationScope(Kind.LINE, filePath, line, null, null, null);
    }

    public static AnnotationScope symbol(String symbolDescription) {
        Objects.requireNonNull(symbolDescription, "symbolDescription");
        if (symbolDescription.isBlank()) {
            throw new IllegalArgumentException("symbolDescription must not be blank");
        }
        return new AnnotationScope(Kind.SYMBOL, null, 0, symbolDescription, null, null);
    }

    public static AnnotationScope change(Change change) {
        Objects.requireNonNull(change, "change");
        return new AnnotationScope(Kind.CHANGE, null, 0, null, ChangeIdentity.of(change), null);
    }

    public static AnnotationScope review() {
        return new AnnotationScope(Kind.REVIEW, null, 0, null, null, null);
    }

    /**
     * A Semantic Canvas item — a territory or a node inside one (ticket
     * #134). {@code canvasItemId} is the canvas's own id scheme
     * (e.g. {@code "territory:crowdness-live"} or
     * {@code "concept:crowdness-live:Idempotent recovery"}) — this scope is
     * opaque to that scheme, it only needs the id to be stable and unique
     * per item so a pin survives a page reload.
     */
    public static AnnotationScope canvasItem(String canvasItemId) {
        Objects.requireNonNull(canvasItemId, "canvasItemId");
        if (canvasItemId.isBlank()) {
            throw new IllegalArgumentException("canvasItemId must not be blank");
        }
        return new AnnotationScope(Kind.CANVAS_ITEM, null, 0, null, null, canvasItemId);
    }

    /** This scope's file+line, if it is a line scope. Empty for every other scope kind. */
    public Optional<LineLocation> lineLocation() {
        return kind == Kind.LINE ? Optional.of(new LineLocation(filePath, line)) : Optional.empty();
    }

    /** This scope's canvas item id, if it is a canvas-item scope. Empty for every other scope kind. */
    public Optional<String> canvasItemId() {
        return kind == Kind.CANVAS_ITEM ? Optional.of(canvasItemId) : Optional.empty();
    }

    /** A line scope's file path and 1-based line number. */
    public record LineLocation(String filePath, int line) {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnnotationScope other)) return false;
        return kind == other.kind && line == other.line
                && Objects.equals(filePath, other.filePath)
                && Objects.equals(symbolDescription, other.symbolDescription)
                && Objects.equals(changeIdentity, other.changeIdentity)
                && Objects.equals(canvasItemId, other.canvasItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, filePath, line, symbolDescription, changeIdentity, canvasItemId);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case LINE -> filePath + ":" + line;
            case SYMBOL -> symbolDescription;
            case CHANGE -> "Change[" + changeIdentity + "]";
            case REVIEW -> "review";
            case CANVAS_ITEM -> "CanvasItem[" + canvasItemId + "]";
        };
    }
}
