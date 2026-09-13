package com.athena.semantic;

import java.util.List;
import java.util.Optional;

/**
 * Classifies a {@link Change} along the {@link SemanticDimension#RESPONSIBILITY}
 * dimension by mapping its {@link TransformationKind} onto the responsibility
 * taxonomy (ticket #86's "meaningful responsibility or capability affected
 * by the change"). Every mapping here is a direct, deterministic reading of
 * the transformation kind itself — no additional structural correlation is
 * needed, so a kind either maps cleanly or is left unclassified rather than
 * forced into an approximate bucket.
 *
 * <p>Deliberately conservative: {@code RENAME_*}/{@code FORMATTING_ONLY}/
 * {@code MECHANICAL_REPLACEMENT} don't change what a type or member is
 * responsible for, only what it's called or how it's written — so they're
 * left unclassified here rather than mapped to a weak guess.
 */
public final class ResponsibilityTaxonomyClassifier {

    private final Taxonomy responsibilityTaxonomy;

    public ResponsibilityTaxonomyClassifier(Taxonomy responsibilityTaxonomy) {
        if (responsibilityTaxonomy.dimension() != SemanticDimension.RESPONSIBILITY) {
            throw new IllegalArgumentException("Expected a RESPONSIBILITY taxonomy, got " + responsibilityTaxonomy.dimension());
        }
        this.responsibilityTaxonomy = responsibilityTaxonomy;
    }

    private Optional<String> conceptIdFor(TransformationKind kind) {
        return switch (kind) {
            case ADD_CLASS, ADD_SYMBOL, ADD_FIELD -> Optional.of("add-capability");
            case REMOVE_CLASS, REMOVE_SYMBOL, REMOVE_FIELD -> Optional.of("remove-capability");
            case MOVE_CLASS, MOVE_SYMBOL, MOVE_FIELD -> Optional.of("move-responsibility");
            case CHANGE_METHOD_SIGNATURE -> Optional.of("change-api-responsibility");
            case EXTRACT_METHOD -> Optional.of("split-responsibility");
            case RENAME_SYMBOL, RENAME_CLASS, RENAME_FIELD, MECHANICAL_REPLACEMENT, FORMATTING_ONLY -> Optional.empty();
        };
    }

    public Optional<SemanticClassification> classify(Change change) {
        if (change.matchedOccurrences().isEmpty()) {
            return Optional.empty();
        }
        return conceptIdFor(change.kind())
                .flatMap(responsibilityTaxonomy::find)
                .map(concept -> SemanticClassification.of(concept, List.copyOf(change.matchedOccurrences())));
    }
}
