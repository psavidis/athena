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
            case MOVE_CLASS, MOVE_SYMBOL, MOVE_FIELD, PULL_UP_FIELD, PULL_UP_SYMBOL -> Optional.of("move-responsibility");
            // An enum constant or annotation element widens or narrows the type's public
            // contract; it isn't a new business capability of its own (ticket #266).
            case CHANGE_METHOD_SIGNATURE, ADD_CONSTRUCTOR_PARAMETER, ADD_ENUM_CONSTANT, REMOVE_ENUM_CONSTANT,
                 ADD_ANNOTATION_ELEMENT, REMOVE_ANNOTATION_ELEMENT, CHANGE_ANNOTATION_ELEMENT_DEFAULT ->
                    Optional.of("change-api-responsibility");
            case EXTRACT_METHOD -> Optional.of("split-responsibility");
            case CHANGE_CONTROL_FLOW -> Optional.of("modify-capability");
            case RENAME_SYMBOL, RENAME_CLASS, RENAME_FIELD, MECHANICAL_REPLACEMENT, FORMATTING_ONLY,
                 CHANGE_FIELD_ANNOTATIONS, CHANGE_FIELD_TYPE, CHANGE_PARAMETER_ANNOTATIONS,
                 CHANGE_METHOD_ANNOTATIONS, MODIFY_METHOD_BODY -> Optional.empty();
            // Only a visibility change alters the contract (ticket #313); see conceptIdFor(Change).
            case CHANGE_MODIFIERS, CHANGE_FIELD_VALUE, CHANGE_SUPERTYPE, REORDER_MEMBERS -> Optional.empty();
            // A build dependency is not a responsibility of the code (ticket #340).
            case ADD_DEPENDENCY, REMOVE_DEPENDENCY, CHANGE_DEPENDENCY -> Optional.empty();
        };
    }

    /** As {@link #conceptIdFor(TransformationKind)}, except that a visibility change is an API change. */
    private Optional<String> conceptIdFor(Change change) {
        if (change.kind() == TransformationKind.CHANGE_MODIFIERS && change.matchedOccurrences().stream()
                .anyMatch(occurrence -> occurrence.involvedDescriptions().get(1).contains(" -> "))) {
            return Optional.of("change-api-responsibility");
        }
        return conceptIdFor(change.kind());
    }

    public Optional<SemanticClassification> classify(Change change) {
        // A test is not a business capability (ticket #285): a new test method isn't "Add Capability".
        if (change.matchedOccurrences().isEmpty() || change.isTestCode()) {
            return Optional.empty();
        }
        return conceptIdFor(change)
                .flatMap(responsibilityTaxonomy::find)
                .map(concept -> SemanticClassification.of(concept, List.copyOf(change.matchedOccurrences())));
    }
}
