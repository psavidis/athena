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

    /**
     * The enclosing type this Change is primarily filed under, for grouping
     * many Changes that touch the same class into one row instead of one
     * per member (epic #5 §14 follow-up: a reviewer seeing "DeviceConfiguration
     * — 3 changes" instead of three disconnected rows). Derived from the
     * first matched occurrence's first involved symbol description
     * ("EnclosingType#member" or a bare type name for a record), which is
     * always the transformation's base/"from" side. Empty if this Change
     * has no matched occurrences to derive one from.
     */
    public String enclosingType() {
        if (matchedOccurrences.isEmpty() || matchedOccurrences.get(0).involvedDescriptions().isEmpty()) {
            return "";
        }
        String description = matchedOccurrences.get(0).involvedDescriptions().get(0);
        int separator = description.indexOf('#');
        return separator < 0 ? description : description.substring(0, separator);
    }

    /**
     * Whether this Change is in test code (ticket #285): every file its matched occurrences
     * touch is a {@linkplain TestSources test source}. A Change touching no files at all, or
     * touching any production file, is not.
     */
    public boolean isTestCode() {
        List<String> files = matchedOccurrences.stream().flatMap(occurrence -> occurrence.filesTouched().stream()).toList();
        return !files.isEmpty() && files.stream().allMatch(TestSources::isTestSource);
    }

    @Override
    public String toString() {
        return title + " (" + occurrenceCount() + " occurrences, " + exceptionCount() + " exceptions)";
    }
}
