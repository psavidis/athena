package com.athena.semantic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One deterministically-detected transformation between a base and head
 * revision: its category, the symbol(s) it involves (described as
 * "EnclosingType#member" strings for the base and/or head side), the files
 * it touches, and — for transformations that collapse many equivalent
 * occurrences (e.g. mechanical replacement) — how many occurrences were
 * folded in.
 */
public final class DetectedTransformation {

    /**
     * Context key: the member's enclosing types, outermost first, one per line as
     * {@code SimpleName<TAB>annotations} (the annotations' source text, space-separated;
     * empty when the type has none).
     */
    public static final String ENCLOSING_TYPE_ANNOTATIONS = "enclosingTypeAnnotations";

    /** Context key: how many files only updated their imports to follow this class move or rename (ticket #337). */
    public static final String IMPORT_FOLLOW_ONS = "importFollowOns";

    /** Context keys: the packages a moved class left and entered (ticket #335). */
    public static final String FROM_PACKAGE = "fromPackage";
    public static final String TO_PACKAGE = "toPackage";

    private final TransformationKind kind;
    private final List<String> involvedDescriptions;
    private final List<String> filesTouched;
    private final int occurrenceCount;
    private final String beforeText;
    private final String afterText;
    private final Map<String, String> context;

    // The diff is real algorithmic work (an LCS over full method source, not
    // just a signature line) — computed lazily and memoized here rather than
    // eagerly for all ~150+ transformations a typical PR detects, since a
    // reviewer only ever opens a handful of Change detail views. See
    // TransformationDetector, whose detect() populates beforeText/afterText
    // for every kind but never calls diffText() itself.
    private String memoizedDiffText;

    private DetectedTransformation(TransformationKind kind, List<String> involvedDescriptions,
                            List<String> filesTouched, int occurrenceCount) {
        this(kind, involvedDescriptions, filesTouched, occurrenceCount, "", "");
    }

    private DetectedTransformation(TransformationKind kind, List<String> involvedDescriptions,
                            List<String> filesTouched, int occurrenceCount, String beforeText, String afterText) {
        this(kind, involvedDescriptions, filesTouched, occurrenceCount, beforeText, afterText, Map.of());
    }

    private DetectedTransformation(TransformationKind kind, List<String> involvedDescriptions,
                            List<String> filesTouched, int occurrenceCount, String beforeText, String afterText,
                            Map<String, String> context) {
        this.kind = kind;
        this.involvedDescriptions = List.copyOf(involvedDescriptions);
        this.filesTouched = List.copyOf(filesTouched);
        this.occurrenceCount = occurrenceCount;
        this.beforeText = beforeText;
        this.afterText = afterText;
        this.context = Map.copyOf(context);
    }

    /**
     * Construction API for a {@link com.athena.semantic.spi.LanguagePlugin}'s own
     * detection logic (e.g. {@code TransformationDetector} in {@code athena-plugin-java})
     * to report a plain single-occurrence transformation with no recorded diff text.
     */
    public static DetectedTransformation of(TransformationKind kind, List<String> involved, List<String> files) {
        return new DetectedTransformation(kind, involved, files, 1);
    }

    /** Construction API for a transformation collapsing more than one equivalent occurrence. */
    public static DetectedTransformation withOccurrences(TransformationKind kind, List<String> involved,
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
    public static DetectedTransformation withDiff(TransformationKind kind, List<String> involved,
                                            List<String> files, String beforeText, String afterText) {
        return new DetectedTransformation(kind, involved, files, 1, beforeText, afterText);
    }

    /**
     * This transformation with one more piece of source context a language plugin knows and a
     * {@link com.athena.semantic.spi.FrameworkPlugin} may need — which only sees Changes, never
     * the source (ticket #295: the annotations on a field's enclosing types). Keys are
     * documented by the plugin that writes them, e.g. {@link #ENCLOSING_TYPE_ANNOTATIONS}.
     */
    public DetectedTransformation withContext(String key, String value) {
        Map<String, String> extended = new LinkedHashMap<>(context);
        extended.put(key, value);
        return new DetectedTransformation(kind, involvedDescriptions, filesTouched, occurrenceCount, beforeText,
                afterText, extended);
    }

    /**
     * This transformation touching {@code files} too (ticket #337): follow-on edits that belong to
     * it, such as imports updated for a moved class. Context is kept.
     */
    public DetectedTransformation withAdditionalFiles(List<String> files) {
        List<String> all = new ArrayList<>(filesTouched);
        files.stream().filter(file -> !all.contains(file)).forEach(all::add);
        return new DetectedTransformation(kind, involvedDescriptions, all, occurrenceCount, beforeText, afterText, context);
    }

    /** Source context recorded with this transformation (see {@link #withContext}); empty if none. */
    public Map<String, String> context() {
        return context;
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
