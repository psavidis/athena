package com.athena.semantic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the same structural change made in several classes (ticket #291): Structural
 * classifications sharing a concept and a member name ("remove" of {@code setBeanFactory})
 * across two or more distinct classes become one {@link RepeatedStructuralChange}. The member
 * name is read from the Change's first involved description ("Type#member"); a Change with no
 * member (e.g. an added class) is never folded. A modifier change (ticket #313) is keyed by
 * its delta instead ("+final"), whatever the member: that's the repeated operation. Groups
 * come out in first-seen order.
 */
public final class RepeatedStructuralChangeGrouper {

    private static final int MINIMUM_CLASSES = 2;

    public List<RepeatedStructuralChange> group(List<SemanticProfile> profiles) {
        Map<String, List<SemanticProfile>> byConceptAndMember = new LinkedHashMap<>();
        for (SemanticProfile profile : profiles) {
            String subject = subjectOf(profile.change());
            if (subject.isEmpty()) {
                continue;
            }
            for (SemanticClassification classification : profile.classifications(SemanticDimension.STRUCTURAL)) {
                byConceptAndMember.computeIfAbsent(classification.concept().id() + "\n" + subject, key -> new ArrayList<>())
                        .add(profile);
            }
        }
        List<RepeatedStructuralChange> groups = new ArrayList<>();
        for (List<SemanticProfile> members : byConceptAndMember.values()) {
            Set<String> classes = new LinkedHashSet<>();
            members.forEach(member -> classes.add(member.change().enclosingType()));
            if (classes.size() >= MINIMUM_CLASSES) {
                groups.add(toGroup(members, List.copyOf(classes)));
            }
        }
        return groups;
    }

    private static RepeatedStructuralChange toGroup(List<SemanticProfile> members, List<String> classes) {
        List<DetectedTransformation> evidence = new ArrayList<>();
        List<SemanticClassification> merged = new ArrayList<>();
        for (SemanticProfile member : members) {
            List<SemanticClassification> structural = member.classifications(SemanticDimension.STRUCTURAL);
            merged.addAll(structural);
            structural.forEach(classification -> evidence.addAll(classification.evidence()));
        }
        TaxonomyConcept concept = merged.get(0).concept();
        return new RepeatedStructuralChange(concept, subjectOf(members.get(0).change()), classes, evidence, merged);
    }

    /** What the repeated change is about: the modifier delta for a modifier change, else the member name. */
    private static String subjectOf(Change change) {
        if (change.matchedOccurrences().isEmpty() || change.matchedOccurrences().get(0).involvedDescriptions().isEmpty()) {
            return "";
        }
        List<String> involved = change.matchedOccurrences().get(0).involvedDescriptions();
        if (change.kind() == TransformationKind.CHANGE_MODIFIERS && involved.size() > 1) {
            return involved.get(1);
        }
        String description = involved.get(0);
        int separator = description.indexOf('#');
        return separator < 0 ? "" : description.substring(separator + 1);
    }
}
