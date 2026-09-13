package com.athena.semantic;

import java.util.List;
import java.util.Optional;

/**
 * Classifies a {@link Change} along the {@link SemanticDimension#ARCHITECTURE}
 * dimension using widely-recognized type-naming conventions (ticket #86:
 * "the model should not assume a single architecture" — this classifier
 * deliberately sticks to conventions common across most layered/hexagonal
 * styles, e.g. a {@code *Controller} driving the application from outside,
 * rather than assuming one specific architecture's exact vocabulary).
 *
 * <p>Naming conventions are a weak signal on their own, so this only fires
 * for {@code ADD_CLASS}/{@code RENAME_CLASS} — a type actually being
 * introduced or renamed to match the convention — not for arbitrary member
 * changes inside an already-named type, where the convention says nothing
 * about what specifically changed.
 */
public final class ArchitectureTaxonomyClassifier {

    private final Taxonomy architectureTaxonomy;

    public ArchitectureTaxonomyClassifier(Taxonomy architectureTaxonomy) {
        if (architectureTaxonomy.dimension() != SemanticDimension.ARCHITECTURE) {
            throw new IllegalArgumentException("Expected an ARCHITECTURE taxonomy, got " + architectureTaxonomy.dimension());
        }
        this.architectureTaxonomy = architectureTaxonomy;
    }

    public Optional<SemanticClassification> classify(Change change) {
        if (change.kind() != TransformationKind.ADD_CLASS && change.kind() != TransformationKind.RENAME_CLASS) {
            return Optional.empty();
        }
        if (change.matchedOccurrences().isEmpty()) {
            return Optional.empty();
        }
        String simpleName = lastSegment(change.matchedOccurrences().get(0).involvedDescriptions());
        return conceptIdFor(simpleName)
                .flatMap(architectureTaxonomy::find)
                .map(concept -> SemanticClassification.of(concept, List.copyOf(change.matchedOccurrences())));
    }

    private String lastSegment(List<String> involvedDescriptions) {
        return involvedDescriptions.get(involvedDescriptions.size() - 1);
    }

    private Optional<String> conceptIdFor(String typeSimpleName) {
        if (typeSimpleName.endsWith("Controller") || typeSimpleName.endsWith("Resource")) {
            return Optional.of("driving-adapter");
        }
        if (typeSimpleName.endsWith("RepositoryImpl") || typeSimpleName.endsWith("Client")
                || typeSimpleName.endsWith("Gateway")) {
            return Optional.of("driven-adapter");
        }
        if (typeSimpleName.endsWith("Repository") || typeSimpleName.endsWith("Port")) {
            return Optional.of("outbound-port");
        }
        if (typeSimpleName.endsWith("Entity")) {
            return Optional.of("entity");
        }
        if (typeSimpleName.endsWith("Event")) {
            return Optional.of("domain-event");
        }
        if (typeSimpleName.endsWith("Service") && !typeSimpleName.endsWith("ApplicationService")) {
            return Optional.of("domain-service");
        }
        return Optional.empty();
    }
}
