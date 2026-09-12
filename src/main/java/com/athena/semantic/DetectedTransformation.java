package com.athena.semantic;

import java.util.List;

/**
 * One deterministically-detected transformation between a base and head
 * revision: its category, the symbol(s) it involves (described as
 * "EnclosingType#member" strings for the base and/or head side), the files
 * it touches, and — for transformations that collapse many equivalent
 * occurrences (e.g. mechanical replacement) — how many occurrences were
 * folded in.
 */
public final class DetectedTransformation {

    private final TransformationKind kind;
    private final List<String> involvedDescriptions;
    private final List<String> filesTouched;
    private final int occurrenceCount;
    private final String diffText;

    DetectedTransformation(TransformationKind kind, List<String> involvedDescriptions,
                            List<String> filesTouched, int occurrenceCount) {
        this(kind, involvedDescriptions, filesTouched, occurrenceCount, "");
    }

    DetectedTransformation(TransformationKind kind, List<String> involvedDescriptions,
                            List<String> filesTouched, int occurrenceCount, String diffText) {
        this.kind = kind;
        this.involvedDescriptions = List.copyOf(involvedDescriptions);
        this.filesTouched = List.copyOf(filesTouched);
        this.occurrenceCount = occurrenceCount;
        this.diffText = diffText;
    }

    static DetectedTransformation of(TransformationKind kind, List<String> involved, List<String> files) {
        return new DetectedTransformation(kind, involved, files, 1);
    }

    static DetectedTransformation withOccurrences(TransformationKind kind, List<String> involved,
                                                   List<String> files, int occurrenceCount) {
        return new DetectedTransformation(kind, involved, files, occurrenceCount);
    }

    /**
     * A transformation carrying its underlying textual diff, so a Change built
     * from it can expose the raw evidence regardless of classification (epic
     * #4 §10: "the system must preserve the underlying textual diff").
     */
    static DetectedTransformation withDiff(TransformationKind kind, List<String> involved,
                                            List<String> files, String diffText) {
        return new DetectedTransformation(kind, involved, files, 1, diffText);
    }

    public TransformationKind kind() {
        return kind;
    }

    /** Human-readable descriptions ("EnclosingType#member") of the symbol(s) involved. */
    public List<String> involvedDescriptions() {
        return involvedDescriptions;
    }

    public List<String> filesTouched() {
        return filesTouched;
    }

    /** How many equivalent occurrences this transformation collapses (1 unless otherwise noted). */
    public int occurrenceCount() {
        return occurrenceCount;
    }

    /** The underlying textual diff for this transformation, or empty if none was recorded. */
    public String diffText() {
        return diffText;
    }

    @Override
    public String toString() {
        return kind + " " + involvedDescriptions + " (x" + occurrenceCount + ")";
    }
}
