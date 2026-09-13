package com.athena.semantic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Classifies a {@link Change} along the {@link SemanticDimension#INTENT}
 * dimension (ticket #93's "why was this changed") by correlating its
 * *other* already-derived {@link SemanticClassification}s, never by
 * inspecting the raw diff directly — Intent is explicitly the most
 * inferential dimension (#91 §9), so it only fires when there is genuine
 * cross-dimension evidence to point to, never a guess.
 *
 * <p>Each correlation rule below names the combination of other-dimension
 * concepts it looks for and the Intent concept(s) it implies, ranked
 * primary-first so a caller (e.g. the frontend) can show the primary
 * inference alongside any lower-confidence alternatives, per #91 §9's
 * "Improve testability primary, with less likely alternatives" example.
 * {@link SemanticProfile} already models a dimension as a list of
 * classifications, so ranking is simply this returned list's order — no
 * new confidence field on {@link SemanticClassification} was needed.
 *
 * <p>A Change with no other-dimension classification matching any rule
 * below is left without an Intent classification entirely, rather than a
 * fabricated one (#91 Limitations of Scope).
 */
public final class IntentTaxonomyClassifier {

    private final Taxonomy intentTaxonomy;

    public IntentTaxonomyClassifier(Taxonomy intentTaxonomy) {
        if (intentTaxonomy.dimension() != SemanticDimension.INTENT) {
            throw new IllegalArgumentException("Expected an INTENT taxonomy, got " + intentTaxonomy.dimension());
        }
        this.intentTaxonomy = intentTaxonomy;
    }

    public List<SemanticClassification> classify(SemanticProfile profile) {
        Optional<SemanticClassification> dependencyInjection = concept(profile, SemanticDimension.PATTERN, "dependency-injection");
        Optional<SemanticClassification> fieldToConstructorInjection =
                concept(profile, SemanticDimension.FRAMEWORK, "spring-field-to-constructor-injection");

        if (dependencyInjection.isPresent() && fieldToConstructorInjection.isPresent()) {
            return rankedClassifications(
                    List.of("reduce-coupling", "improve-maintainability"),
                    mergedEvidence(dependencyInjection.get(), fieldToConstructorInjection.get()));
        }
        return List.of();
    }

    private Optional<SemanticClassification> concept(SemanticProfile profile, SemanticDimension dimension, String conceptId) {
        return profile.classifications(dimension).stream()
                .filter(classification -> classification.concept().id().equals(conceptId))
                .findFirst();
    }

    /**
     * The correlating classifications' evidence, deduplicated: every existing classifier
     * passes a Change's own {@code matchedOccurrences()} verbatim as its evidence, so two
     * classifications derived from the same Change typically share the exact same
     * {@link DetectedTransformation} instances — a plain concatenation would show a reviewer
     * each one twice under #91's "Show evidence" affordance. A {@link LinkedHashSet} is
     * enough to dedupe here (no need for {@code DetectedTransformation} to define its own
     * equals/hashCode) since these are literally the same object references, and it keeps
     * first-seen order so the primary correlation's evidence still leads.
     */
    private List<DetectedTransformation> mergedEvidence(SemanticClassification... correlating) {
        Set<DetectedTransformation> merged = new LinkedHashSet<>();
        for (SemanticClassification classification : correlating) {
            merged.addAll(classification.evidence());
        }
        return new ArrayList<>(merged);
    }

    private List<SemanticClassification> rankedClassifications(List<String> conceptIdsRankedPrimaryFirst,
                                                                 List<DetectedTransformation> evidence) {
        List<SemanticClassification> classifications = new ArrayList<>();
        for (String conceptId : conceptIdsRankedPrimaryFirst) {
            intentTaxonomy.find(conceptId)
                    .ifPresent(concept -> classifications.add(SemanticClassification.of(concept, evidence)));
        }
        return classifications;
    }
}
