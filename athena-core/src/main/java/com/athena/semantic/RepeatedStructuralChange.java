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
    private final String subject;
    private final List<String> classes;
    private final List<DetectedTransformation> evidence;
    private final List<SemanticClassification> mergedClassifications;

    RepeatedStructuralChange(TaxonomyConcept concept, String subject, List<String> classes,
                             List<DetectedTransformation> evidence, List<SemanticClassification> mergedClassifications) {
        this.concept = concept;
        this.subject = subject;
        this.classes = List.copyOf(classes);
        this.evidence = List.copyOf(evidence);
        this.mergedClassifications = List.copyOf(mergedClassifications);
    }

    public TaxonomyConcept concept() {
        return concept;
    }

    /** The member name the change repeats on, or for a modifier change its delta, e.g. "+final". */
    public String subject() {
        return subject;
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
