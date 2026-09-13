package com.athena.semantic;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Classifies a {@link Change} along the {@link SemanticDimension#FEATURE}
 * dimension (ticket #92's "which feature/flow does it belong to"). Unlike
 * {@link ResponsibilityTaxonomyClassifier}'s direct {@link TransformationKind}
 * mapping, Flow/Feature concepts (ticket #86's {@code feature.json}) are
 * product-specific, not generic vocabulary, so this correlates a Change's
 * own involved symbol names against each flow concept's name instead of
 * switching on the transformation kind — the same "an add/rename actually
 * takes on the name" signal {@link ArchitectureTaxonomyClassifier}/
 * {@link PatternTaxonomyClassifier} use, but not restricted to those two
 * kinds, since a flow concept can plausibly correlate with any kind of
 * change to a symbol already carrying its name.
 *
 * <p>Only leaf concepts (those declaring a {@code parentId}) are eligible —
 * a taxonomy's top-level capability entries (e.g. {@code order-management})
 * describe the wider business capability, not "which feature/flow", and
 * matching against them would blur this dimension with
 * {@link SemanticDimension#RESPONSIBILITY}.
 */
public final class FlowTaxonomyClassifier {

    private final Taxonomy featureTaxonomy;

    public FlowTaxonomyClassifier(Taxonomy featureTaxonomy) {
        if (featureTaxonomy.dimension() != SemanticDimension.FEATURE) {
            throw new IllegalArgumentException("Expected a FEATURE taxonomy, got " + featureTaxonomy.dimension());
        }
        this.featureTaxonomy = featureTaxonomy;
    }

    public Optional<SemanticClassification> classify(Change change) {
        if (change.matchedOccurrences().isEmpty()) {
            return Optional.empty();
        }
        List<String> involvedDescriptions = change.matchedOccurrences().get(0).involvedDescriptions();
        return correlatingConcept(involvedDescriptions)
                .map(concept -> SemanticClassification.of(concept, List.copyOf(change.matchedOccurrences())));
    }

    private Optional<TaxonomyConcept> correlatingConcept(List<String> involvedDescriptions) {
        for (TaxonomyConcept concept : featureTaxonomy.concepts()) {
            if (concept.parentId().isEmpty()) {
                continue;
            }
            String normalizedConceptName = normalize(concept.name());
            boolean correlates = involvedDescriptions.stream()
                    .anyMatch(description -> normalize(description).contains(normalizedConceptName));
            if (correlates) {
                return Optional.of(concept);
            }
        }
        return Optional.empty();
    }

    private String normalize(String text) {
        return text.replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
