package com.athena.semantic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * A Change's stable identity: its symbol-set plus its transformation-shape
 * (kind) — not line numbers, diff hunks, or commit SHAs (epic #4 §28). Two
 * Changes computed from different revisions of "the same" underlying
 * transformation produce equal {@code ChangeIdentity} values, which is what
 * lets the same conceptual Change be recognized across a force-push or
 * rewritten commit history.
 *
 * <p>This class only computes and compares identity keys — it does not
 * persist or track continuity across revisions over time. That is Epic #6's
 * concern; this is the reusable groundwork it builds on.
 */
public final class ChangeIdentity {

    private final TransformationKind transformationShape;
    private final Set<String> symbolSet;

    private ChangeIdentity(TransformationKind transformationShape, Set<String> symbolSet) {
        this.transformationShape = transformationShape;
        this.symbolSet = symbolSet;
    }

    public static ChangeIdentity of(Change change) {
        Set<String> symbols = new TreeSet<>();
        for (DetectedTransformation occurrence : change.matchedOccurrences()) {
            symbols.addAll(occurrence.involvedDescriptions());
        }
        return new ChangeIdentity(change.kind(), symbols);
    }

    /** The transformation category this identity is keyed on (rename/move/extract/...). */
    public TransformationKind transformationShape() {
        return transformationShape;
    }

    /** The set of symbol descriptions this identity is keyed on. */
    public Set<String> symbolSet() {
        return symbolSet;
    }

    /** Finds the Change in {@code candidates} whose own identity equals {@code key}, if any. */
    public static Optional<Change> findMatching(ChangeIdentity key, List<Change> candidates) {
        return candidates.stream()
                .filter(candidate -> ChangeIdentity.of(candidate).equals(key))
                .findFirst();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChangeIdentity other)) return false;
        return transformationShape == other.transformationShape && symbolSet.equals(other.symbolSet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transformationShape, symbolSet);
    }

    @Override
    public String toString() {
        return transformationShape + ":" + symbolSet;
    }
}
