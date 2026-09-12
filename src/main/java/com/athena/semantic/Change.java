package com.athena.semantic;

import java.util.List;

/**
 * A meaningful conceptual transformation, materialized by grouping one or
 * more individually-detected transformations that represent the same
 * conceptual operation (see epic #4 §8/§11). Occurrences that resemble the
 * group's pattern without matching it structurally are kept as exceptions:
 * linked to this Change, but distinguishable and separately inspectable
 * (§12).
 */
public final class Change {

    private final String title;
    private final TransformationKind kind;
    private final List<DetectedTransformation> matchedOccurrences;
    private final List<DetectedTransformation> exceptions;

    Change(String title, TransformationKind kind, List<DetectedTransformation> matchedOccurrences,
           List<DetectedTransformation> exceptions) {
        this.title = title;
        this.kind = kind;
        this.matchedOccurrences = List.copyOf(matchedOccurrences);
        this.exceptions = List.copyOf(exceptions);
    }

    public String title() {
        return title;
    }

    public TransformationKind kind() {
        return kind;
    }

    /** The transformations that structurally match this Change's own pattern. */
    public List<DetectedTransformation> matchedOccurrences() {
        return matchedOccurrences;
    }

    /** How many equivalent occurrences this Change represents (sum of each occurrence's own count). */
    public int occurrenceCount() {
        return matchedOccurrences.stream().mapToInt(DetectedTransformation::occurrenceCount).sum();
    }

    /** Transformations that resemble this Change's pattern but don't structurally match it. */
    public List<DetectedTransformation> exceptions() {
        return exceptions;
    }

    public int exceptionCount() {
        return exceptions.size();
    }

    @Override
    public String toString() {
        return title + " (" + occurrenceCount() + " occurrences, " + exceptionCount() + " exceptions)";
    }
}
