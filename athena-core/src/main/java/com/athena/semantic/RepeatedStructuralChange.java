package com.athena.semantic;

import java.util.List;

/**
 * The same structural change — one Structural concept on one member name — made in two or
 * more classes, folded into one entry for the Explorer (ticket #291): "Remove setBeanFactory
 * in 5 classes". {@link #mergedClassifications()} are the per-Change Structural
 * classifications it replaces.
 */
public final class RepeatedStructuralChange {

    private final TaxonomyConcept concept;
    private final String member;
    private final List<String> classes;
    private final List<DetectedTransformation> evidence;
    private final List<SemanticClassification> mergedClassifications;

    RepeatedStructuralChange(TaxonomyConcept concept, String member, List<String> classes,
                             List<DetectedTransformation> evidence, List<SemanticClassification> mergedClassifications) {
        this.concept = concept;
        this.member = member;
        this.classes = List.copyOf(classes);
        this.evidence = List.copyOf(evidence);
        this.mergedClassifications = List.copyOf(mergedClassifications);
    }

    public TaxonomyConcept concept() {
        return concept;
    }

    public String member() {
        return member;
    }

    /** The classes the change was made in, in first-seen order. */
    public List<String> classes() {
        return classes;
    }

    public List<DetectedTransformation> evidence() {
        return evidence;
    }

    /** The distinct files the folded Changes touch, in first-seen order. */
    public List<String> filesTouched() {
        return evidence.stream().flatMap(occurrence -> occurrence.filesTouched().stream()).distinct().toList();
    }

    public List<SemanticClassification> mergedClassifications() {
        return mergedClassifications;
    }
}
