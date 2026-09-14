package com.athena.semantic;

import java.util.List;

/**
 * One capability extraction {@link CapabilitySplitDetector} found: {@code
 * moveCount} {@code move-responsibility} classifications that all move from
 * {@code sourceModule} to {@code destinationModule}, folded into one {@code
 * capability-extraction} classification so a reviewer sees the pattern
 * instead of one repeated card per move. {@link #mergedClassifications()}
 * are the original per-move classifications the group replaces, so a
 * response builder can drop them from a flat per-Change list before adding
 * this group's own classification in their place.
 */
public final class CapabilitySplitGroup {

    private final String sourceModule;
    private final String destinationModule;
    private final SemanticClassification classification;
    private final List<SemanticClassification> mergedClassifications;

    CapabilitySplitGroup(String sourceModule, String destinationModule, SemanticClassification classification,
                          List<SemanticClassification> mergedClassifications) {
        this.sourceModule = sourceModule;
        this.destinationModule = destinationModule;
        this.classification = classification;
        this.mergedClassifications = List.copyOf(mergedClassifications);
    }

    public String sourceModule() {
        return sourceModule;
    }

    public String destinationModule() {
        return destinationModule;
    }

    /** How many individual moves this group represents. */
    public int moveCount() {
        return mergedClassifications.size();
    }

    /** The single {@code capability-extraction} classification standing in for every move in this group. */
    public SemanticClassification classification() {
        return classification;
    }

    /** The original {@code move-responsibility} classifications this group folds together. */
    public List<SemanticClassification> mergedClassifications() {
        return mergedClassifications;
    }
}
