package com.athena.semantic;

import java.util.List;
import java.util.Optional;

/**
 * Classifies a {@link Change} along the {@link SemanticDimension#STRUCTURAL}
 * dimension by mapping its {@link TransformationKind} onto the structural
 * taxonomy (ticket #86's lowest level: "atomic/mechanical changes detected
 * from the diff", already produced deterministically by
 * {@link TransformationDetector} — this classifier only translates that
 * existing, stable result into a {@link SemanticClassification} against a
 * taxonomy concept id).
 *
 * <p>Every other dimension (pattern, framework, responsibility, feature,
 * architecture, intent) has no classifier yet by design (ticket #86's
 * scope limits) — {@link Taxonomy} and {@link SemanticProfile} already
 * support them the moment a classifier is introduced, without any change
 * to this class or {@link TransformationDetector}.
 */
public final class StructuralTaxonomyClassifier {

    private final Taxonomy structuralTaxonomy;

    public StructuralTaxonomyClassifier(Taxonomy structuralTaxonomy) {
        if (structuralTaxonomy.dimension() != SemanticDimension.STRUCTURAL) {
            throw new IllegalArgumentException("Expected a STRUCTURAL taxonomy, got " + structuralTaxonomy.dimension());
        }
        this.structuralTaxonomy = structuralTaxonomy;
    }

    /** The structural concept id every {@link TransformationKind} maps onto — the taxonomy's stable ids. */
    private String conceptIdFor(TransformationKind kind) {
        return switch (kind) {
            case RENAME_SYMBOL, RENAME_CLASS, RENAME_FIELD -> "rename";
            case MOVE_SYMBOL, MOVE_CLASS, MOVE_FIELD -> "move";
            case ADD_SYMBOL, ADD_CLASS, ADD_FIELD -> "add";
            case REMOVE_SYMBOL, REMOVE_CLASS, REMOVE_FIELD -> "remove";
            case CHANGE_METHOD_SIGNATURE -> "change-signature";
            case EXTRACT_METHOD -> "extract-method";
            case MECHANICAL_REPLACEMENT -> "mechanical-replacement";
            case FORMATTING_ONLY -> "formatting-only";
        };
    }

    /**
     * Classifies {@code change} using its first matched occurrence's kind — a Change's
     * matched occurrences all share one {@link TransformationKind} by construction (see
     * {@link ChangeGrouper}), so any one of them determines the structural concept for the
     * whole Change. Empty if the Change has no matched occurrences to classify from.
     */
    public Optional<SemanticClassification> classify(Change change) {
        if (change.matchedOccurrences().isEmpty()) {
            return Optional.empty();
        }
        String conceptId = conceptIdFor(change.kind());
        Optional<TaxonomyConcept> concept = structuralTaxonomy.find(conceptId);
        if (concept.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(SemanticClassification.of(concept.get(), List.copyOf(change.matchedOccurrences())));
    }
}
