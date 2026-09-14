package com.athena.semantic;

import java.util.List;

/**
 * A run of {@link RepeatedClassificationGrouper#detect repeated} identical
 * classifications — the same {@link TaxonomyConcept} recurring across
 * several Changes — folded into one representative classification so a
 * reviewer sees one card with a count instead of one repeated card per
 * Change. {@link #mergedClassifications()} are the originals this group
 * replaces, so a response builder can drop them from a flat per-Change list
 * before adding this group's own classification in their place.
 */
public final class RepeatedClassificationGroup {

    private final SemanticClassification classification;
    private final List<SemanticClassification> mergedClassifications;

    RepeatedClassificationGroup(SemanticClassification classification, List<SemanticClassification> mergedClassifications) {
        this.classification = classification;
        this.mergedClassifications = List.copyOf(mergedClassifications);
    }

    /** How many individual Changes this group represents. */
    public int occurrenceCount() {
        return mergedClassifications.size();
    }

    /** The single classification standing in for every occurrence in this group. */
    public SemanticClassification classification() {
        return classification;
    }

    /** The original classifications this group folds together. */
    public List<SemanticClassification> mergedClassifications() {
        return mergedClassifications;
    }
}
