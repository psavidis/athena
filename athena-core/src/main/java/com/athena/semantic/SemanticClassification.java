package com.athena.semantic;

import java.util.List;
import java.util.Objects;

/**
 * One classification of a {@link Change} against a single
 * {@link TaxonomyConcept}, together with the provenance (which of the
 * Change's own detected transformations support it) that lets a reviewer
 * trace a higher-level interpretation back to the low-level facts it was
 * built from (ticket #86: "a higher-level interpretation should be
 * traceable to the lower-level changes supporting it").
 */
public final class SemanticClassification {

    private final TaxonomyConcept concept;
    private final List<DetectedTransformation> evidence;
    private final List<String> supportingConceptNames;

    private SemanticClassification(TaxonomyConcept concept, List<DetectedTransformation> evidence,
                                    List<String> supportingConceptNames) {
        this.concept = concept;
        this.evidence = List.copyOf(evidence);
        this.supportingConceptNames = List.copyOf(supportingConceptNames);
    }

    public static SemanticClassification of(TaxonomyConcept concept, List<DetectedTransformation> evidence) {
        return of(concept, evidence, List.of());
    }

    /**
     * @param supportingConceptNames the concept names of other classifications (typically this
     *        same Change's own {@link SemanticDimension#STRUCTURAL} entry, and — for a
     *        multi-Change correlation like Dependency Injection — the Change it correlated
     *        with) that a reviewer can follow to see what this classification is built from
     *        (ticket #96).
     */
    public static SemanticClassification of(TaxonomyConcept concept, List<DetectedTransformation> evidence,
                                             List<String> supportingConceptNames) {
        Objects.requireNonNull(concept, "concept");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(supportingConceptNames, "supportingConceptNames");
        return new SemanticClassification(concept, evidence, supportingConceptNames);
    }

    public TaxonomyConcept concept() {
        return concept;
    }

    /** The detected transformations this classification was derived from. */
    public List<DetectedTransformation> evidence() {
        return evidence;
    }

    /** The concept names of other classifications this one is built from/supported by, if any. */
    public List<String> supportingConceptNames() {
        return supportingConceptNames;
    }

    @Override
    public String toString() {
        return concept + " <- " + evidence.size() + " transformation(s)";
    }
}
