package com.athena.semantic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recognizes the same classification recurring across several Changes in a
 * module/PR-scoped set of {@link SemanticProfile}s (e.g. many Changes each
 * independently classified {@code Add Capability} in the RESPONSIBILITY
 * dimension) and folds the run into one representative card with a count,
 * the same "one card, not one per Change" treatment {@link
 * CapabilitySplitDetector} already gives {@code move-responsibility}
 * — which is why {@link #detect} skips that concept (and {@code
 * capability-extraction}, an output concept that is never itself a raw
 * classification) rather than double-grouping it.
 *
 * <p>Grouping is scoped to one {@link SemanticDimension} at a time (the
 * caller runs this once per dimension whose repeated cards should collapse)
 * and keyed by concept id alone — a Change's own evidence/supporting
 * concepts don't affect which group it falls into, only how many Changes
 * that group represents.
 */
public final class RepeatedClassificationGrouper {

    private static final int MINIMUM_OCCURRENCES_TO_COUNT_AS_A_GROUP = 2;
    private static final Set<String> EXCLUDED_CONCEPT_IDS =
            Set.of("move-responsibility", "capability-extraction");

    /**
     * Groups {@code profiles}' {@code dimension} classifications by concept
     * id. A concept reached by only one classification stays a plain,
     * ungrouped entry — {@link RepeatedClassificationGroup} only ever
     * represents {@value #MINIMUM_OCCURRENCES_TO_COUNT_AS_A_GROUP} or more.
     */
    public List<RepeatedClassificationGroup> detect(List<SemanticProfile> profiles, SemanticDimension dimension) {
        Map<String, List<SemanticClassification>> byConceptId = new LinkedHashMap<>();
        for (SemanticProfile profile : profiles) {
            for (SemanticClassification classification : profile.classifications(dimension)) {
                String conceptId = classification.concept().id();
                if (EXCLUDED_CONCEPT_IDS.contains(conceptId)) {
                    continue;
                }
                byConceptId.computeIfAbsent(conceptId, id -> new ArrayList<>()).add(classification);
            }
        }

        List<RepeatedClassificationGroup> groups = new ArrayList<>();
        for (List<SemanticClassification> classifications : byConceptId.values()) {
            if (classifications.size() < MINIMUM_OCCURRENCES_TO_COUNT_AS_A_GROUP) {
                continue;
            }
            groups.add(toGroup(classifications));
        }
        return groups;
    }

    private RepeatedClassificationGroup toGroup(List<SemanticClassification> classifications) {
        List<DetectedTransformation> evidence = new ArrayList<>();
        Set<String> supportingConceptNames = new LinkedHashSet<>();
        for (SemanticClassification classification : classifications) {
            evidence.addAll(classification.evidence());
            supportingConceptNames.addAll(classification.supportingConceptNames());
        }
        TaxonomyConcept concept = classifications.get(0).concept();
        SemanticClassification merged = SemanticClassification.of(concept, evidence, List.copyOf(supportingConceptNames));
        return new RepeatedClassificationGroup(merged, classifications);
    }
}
