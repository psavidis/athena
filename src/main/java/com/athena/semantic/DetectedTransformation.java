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
    private final String beforeText;
    private final String afterText;

    // The diff is real algorithmic work (an LCS over full method source, not
    // just a signature line) — computed lazily and memoized here rather than
    // eagerly for all ~150+ transformations a typical PR detects, since a
    // reviewer only ever opens a handful of Change detail views. See
    // TransformationDetector, whose detect() populates beforeText/afterText
    // for every kind but never calls diffText() itself.
    private String memoizedDiffText;

    DetectedTransformation(TransformationKind kind, List<String> involvedDescriptions,
                            List<String> filesTouched, int occurrenceCount) {
        this(kind, involvedDescriptions, filesTouched, occurrenceCount, "", "");
    }

    DetectedTransformation(TransformationKind kind, List<String> involvedDescriptions,
                            List<String> filesTouched, int occurrenceCount, String beforeText, String afterText) {
        this.kind = kind;
        this.involvedDescriptions = List.copyOf(involvedDescriptions);
        this.filesTouched = List.copyOf(filesTouched);
        this.occurrenceCount = occurrenceCount;
        this.beforeText = beforeText;
        this.afterText = afterText;
    }

    static DetectedTransformation of(TransformationKind kind, List<String> involved, List<String> files) {
        return new DetectedTransformation(kind, involved, files, 1);
    }

    static DetectedTransformation withOccurrences(TransformationKind kind, List<String> involved,
                                                   List<String> files, int occurrenceCount) {
        return new DetectedTransformation(kind, involved, files, occurrenceCount);
    }

    /**
     * A transformation carrying the raw before/after text its diff will be
     * computed from on first request, so a Change built from it can expose
     * the underlying evidence regardless of classification (epic #4 §10:
     * "the system must preserve the underlying textual diff") without
     * paying the diff cost for every transformation up front.
     */
    static DetectedTransformation withDiff(TransformationKind kind, List<String> involved,
                                            List<String> files, String beforeText, String afterText) {
        return new DetectedTransformation(kind, involved, files, 1, beforeText, afterText);
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

    /**
     * The underlying textual diff for this transformation, or empty if none
     * was recorded. Computed on first call and cached — safe to call
     * repeatedly, including concurrently, since {@link UnifiedDiff#of} is a
     * pure function of the immutable before/after text.
     */
    public synchronized String diffText() {
        if (memoizedDiffText == null) {
            memoizedDiffText = (beforeText.isEmpty() && afterText.isEmpty()) ? "" : UnifiedDiff.of(beforeText, afterText);
        }
        return memoizedDiffText;
    }

    @Override
    public String toString() {
        return kind + " " + involvedDescriptions + " (x" + occurrenceCount + ")";
    }
}
