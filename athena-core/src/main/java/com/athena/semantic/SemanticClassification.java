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

    private SemanticClassification(TaxonomyConcept concept, List<DetectedTransformation> evidence) {
        this.concept = concept;
        this.evidence = List.copyOf(evidence);
    }

    public static SemanticClassification of(TaxonomyConcept concept, List<DetectedTransformation> evidence) {
        Objects.requireNonNull(concept, "concept");
        Objects.requireNonNull(evidence, "evidence");
        return new SemanticClassification(concept, evidence);
    }

    public TaxonomyConcept concept() {
        return concept;
    }

    /** The detected transformations this classification was derived from. */
    public List<DetectedTransformation> evidence() {
        return evidence;
    }

    @Override
    public String toString() {
        return concept + " <- " + evidence.size() + " transformation(s)";
    }
}
