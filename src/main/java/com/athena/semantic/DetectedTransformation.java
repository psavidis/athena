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

    DetectedTransformation(TransformationKind kind, List<String> involvedDescriptions,
                            List<String> filesTouched, int occurrenceCount) {
        this.kind = kind;
        this.involvedDescriptions = List.copyOf(involvedDescriptions);
        this.filesTouched = List.copyOf(filesTouched);
        this.occurrenceCount = occurrenceCount;
    }

    static DetectedTransformation of(TransformationKind kind, List<String> involved, List<String> files) {
        return new DetectedTransformation(kind, involved, files, 1);
    }

    static DetectedTransformation withOccurrences(TransformationKind kind, List<String> involved,
                                                   List<String> files, int occurrenceCount) {
        return new DetectedTransformation(kind, involved, files, occurrenceCount);
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

    @Override
    public String toString() {
        return kind + " " + involvedDescriptions + " (x" + occurrenceCount + ")";
    }
}
