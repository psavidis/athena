package com.athena.semantic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The concrete evidence backing a Change: the files and symbols it touches,
 * and its underlying textual diff — retrievable regardless of how the
 * Change was classified (epic #4 §10: classification must never hide or
 * replace the raw diff).
 */
public final class ChangeEvidence {

    private final Set<String> files;
    private final Set<String> symbols;
    private final String textualDiff;

    private ChangeEvidence(Set<String> files, Set<String> symbols, String textualDiff) {
        this.files = files;
        this.symbols = symbols;
        this.textualDiff = textualDiff;
    }

    public static ChangeEvidence of(Change change) {
        Set<String> files = new LinkedHashSet<>();
        Set<String> symbols = new LinkedHashSet<>();
        StringBuilder diff = new StringBuilder();

        List<DetectedTransformation> all = concat(change.matchedOccurrences(), change.exceptions());
        for (DetectedTransformation occurrence : all) {
            files.addAll(occurrence.filesTouched());
            symbols.addAll(occurrence.involvedDescriptions());
            if (!occurrence.diffText().isBlank()) {
                if (diff.length() > 0) {
                    diff.append('\n');
                }
                diff.append(occurrence.diffText());
            }
        }

        return new ChangeEvidence(files, symbols, diff.toString());
    }

    private static List<DetectedTransformation> concat(List<DetectedTransformation> a, List<DetectedTransformation> b) {
        List<DetectedTransformation> combined = new java.util.ArrayList<>(a);
        combined.addAll(b);
        return combined;
    }

    /** Every file this Change (including its exceptions) touches. */
    public Set<String> files() {
        return files;
    }

    /** Every symbol this Change (including its exceptions) touches. */
    public Set<String> symbols() {
        return symbols;
    }

    /** The underlying textual diff, preserved regardless of classification. Empty if none was recorded. */
    public String textualDiff() {
        return textualDiff;
    }
}
