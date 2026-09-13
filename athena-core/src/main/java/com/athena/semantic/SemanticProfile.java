package com.athena.semantic;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The full set of semantic interpretations attached to one {@link Change}
 * (ticket #86), keyed by {@link SemanticDimension}. Deliberately a map of
 * independent dimensions rather than a single linear hierarchy — a Change
 * can carry a Pattern classification, a Framework classification, an
 * Architecture classification and an Intent classification all at once,
 * none of them derived from the others (ticket #86: "do not force these
 * concepts into a single linear hierarchy if doing so loses information").
 *
 * <p>A dimension absent from this profile simply means nothing classified
 * the Change along that dimension yet — not an error, and not a
 * placeholder to eventually fill in for every dimension.
 */
public final class SemanticProfile {

    private final Change change;
    private final Map<SemanticDimension, List<SemanticClassification>> classificationsByDimension;

    private SemanticProfile(Change change, Map<SemanticDimension, List<SemanticClassification>> classificationsByDimension) {
        this.change = change;
        this.classificationsByDimension = classificationsByDimension;
    }

    public static SemanticProfile of(Change change, Map<SemanticDimension, List<SemanticClassification>> classifications) {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(classifications, "classifications");
        Map<SemanticDimension, List<SemanticClassification>> copy = new EnumMap<>(SemanticDimension.class);
        classifications.forEach((dimension, list) -> copy.put(dimension, List.copyOf(list)));
        return new SemanticProfile(change, copy);
    }

    public static SemanticProfile empty(Change change) {
        return of(change, new LinkedHashMap<>());
    }

    public Change change() {
        return change;
    }

    /** This Change's classifications along {@code dimension}, or empty if none were derived. */
    public List<SemanticClassification> classifications(SemanticDimension dimension) {
        return classificationsByDimension.getOrDefault(dimension, List.of());
    }

    /** Every dimension this Change has at least one classification for. */
    public java.util.Set<SemanticDimension> classifiedDimensions() {
        return classificationsByDimension.keySet();
    }

    /** A new profile with {@code classification} added under {@code dimension}, alongside any already present there. */
    public SemanticProfile with(SemanticDimension dimension, SemanticClassification classification) {
        Map<SemanticDimension, List<SemanticClassification>> updated = new EnumMap<>(SemanticDimension.class);
        classificationsByDimension.forEach((d, list) -> updated.put(d, new java.util.ArrayList<>(list)));
        updated.computeIfAbsent(dimension, d -> new java.util.ArrayList<>()).add(classification);
        return of(change, updated);
    }
}
