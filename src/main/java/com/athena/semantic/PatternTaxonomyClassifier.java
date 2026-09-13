package com.athena.semantic;

import java.util.List;
import java.util.Optional;

/**
 * Classifies a {@link Change} along the {@link SemanticDimension#PATTERN}
 * dimension using widely-recognized type-naming conventions (ticket #86's
 * "recognizable implementation techniques or design patterns"). Only fires
 * for {@code ADD_CLASS}/{@code RENAME_CLASS}, same reasoning as
 * {@link ArchitectureTaxonomyClassifier}: a naming convention only speaks
 * to a type actually taking on that name, not to arbitrary changes inside
 * an already-named type.
 *
 * <p>Patterns that can't be recognized this way — Strategy, Observer,
 * Decorator, and most of the taxonomy's other entries — genuinely need
 * multi-symbol structural shape recognition (e.g. "an interface with one
 * abstract method implemented by two unrelated types") that isn't available
 * from a single {@link Change} today, so they're deliberately left
 * unclassified rather than guessed at.
 */
public final class PatternTaxonomyClassifier {

    private final Taxonomy patternTaxonomy;

    public PatternTaxonomyClassifier(Taxonomy patternTaxonomy) {
        if (patternTaxonomy.dimension() != SemanticDimension.PATTERN) {
            throw new IllegalArgumentException("Expected a PATTERN taxonomy, got " + patternTaxonomy.dimension());
        }
        this.patternTaxonomy = patternTaxonomy;
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
                .flatMap(patternTaxonomy::find)
                .map(concept -> SemanticClassification.of(concept, List.copyOf(change.matchedOccurrences())));
    }

    private String lastSegment(List<String> involvedDescriptions) {
        return involvedDescriptions.get(involvedDescriptions.size() - 1);
    }

    private Optional<String> conceptIdFor(String typeSimpleName) {
        if (typeSimpleName.endsWith("Builder")) {
            return Optional.of("builder");
        }
        if (typeSimpleName.endsWith("Factory")) {
            return Optional.of("factory");
        }
        if (typeSimpleName.endsWith("Mapper") || typeSimpleName.endsWith("Converter")) {
            return Optional.of("mapper");
        }
        if (typeSimpleName.endsWith("Repository")) {
            return Optional.of("repository");
        }
        if (typeSimpleName.endsWith("Specification")) {
            return Optional.of("specification");
        }
        if (typeSimpleName.endsWith("Dto") || typeSimpleName.endsWith("DTO")) {
            return Optional.of("dto");
        }
        if (typeSimpleName.endsWith("Facade")) {
            return Optional.of("facade");
        }
        if (typeSimpleName.endsWith("Proxy")) {
            return Optional.of("proxy");
        }
        if (typeSimpleName.endsWith("Adapter")) {
            return Optional.of("adapter");
        }
        if (typeSimpleName.endsWith("Decorator")) {
            return Optional.of("decorator");
        }
        return Optional.empty();
    }
}
