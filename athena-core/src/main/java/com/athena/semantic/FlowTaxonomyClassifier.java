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
 * involved type name against each flow concept's name instead of switching
 * on the transformation kind — the same "an add/rename actually takes on
 * the name" signal {@link ArchitectureTaxonomyClassifier}/
 * {@link PatternTaxonomyClassifier} use, but not restricted to those two
 * kinds, since a flow concept can plausibly correlate with any kind of
 * change to a type already carrying its name.
 *
 * <p>Only the enclosing type's own simple name is checked — never a member
 * name — for the same reason {@code ArchitectureTaxonomyClassifier}/
 * {@code PatternTaxonomyClassifier} restrict themselves to a type actually
 * taking on a name: a member name merely containing a concept's name (e.g.
 * a {@code loginReminder} field on an unrelated class) says nothing about
 * which flow the change belongs to, and would otherwise be a forced/guessed
 * mapping.
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
        String enclosingTypeName = enclosingTypeName(change.matchedOccurrences().get(0).involvedDescriptions());
        return correlatingConcept(enclosingTypeName)
                .map(concept -> SemanticClassification.of(concept, List.copyOf(change.matchedOccurrences())));
    }

    /**
     * The simple name of the type a transformation's most current description names —
     * the last involved description (the "to" side of a rename, matching
     * {@code ArchitectureTaxonomyClassifier}'s convention of classifying a rename into a
     * convention as well as a fresh add), stripped of any {@code #member} suffix.
     */
    private String enclosingTypeName(List<String> involvedDescriptions) {
        String lastDescription = involvedDescriptions.get(involvedDescriptions.size() - 1);
        int separator = lastDescription.indexOf('#');
        return separator < 0 ? lastDescription : lastDescription.substring(0, separator);
    }

    private Optional<TaxonomyConcept> correlatingConcept(String enclosingTypeName) {
        String normalizedTypeName = normalize(enclosingTypeName);
        for (TaxonomyConcept concept : featureTaxonomy.concepts()) {
            if (concept.parentId().isEmpty()) {
                continue;
            }
            if (normalizedTypeName.contains(normalize(concept.name()))) {
                return Optional.of(concept);
            }
        }
        return Optional.empty();
    }

    private String normalize(String text) {
        return text.replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
