package com.athena.semantic;

import java.util.Objects;
import java.util.Optional;

/**
 * One concept within a {@link SemanticDimension}'s taxonomy (ticket #86):
 * a stable id, a human-readable name/description, and an optional parent
 * concept id for taxonomies where a hierarchical relationship is useful
 * (e.g. "Factory Method" under "Factory"). The id is stable across
 * taxonomy edits so a {@link SemanticClassification} can reference a
 * concept independently of its current name/description wording.
 */
public final class TaxonomyConcept {

    private final String id;
    private final SemanticDimension dimension;
    private final String name;
    private final String description;
    private final Optional<String> parentId;

    private TaxonomyConcept(String id, SemanticDimension dimension, String name, String description,
                             Optional<String> parentId) {
        this.id = id;
        this.dimension = dimension;
        this.name = name;
        this.description = description;
        this.parentId = parentId;
    }

    public static TaxonomyConcept of(String id, SemanticDimension dimension, String name, String description,
                                      Optional<String> parentId) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(parentId, "parentId");
        return new TaxonomyConcept(id, dimension, name, description, parentId);
    }

    public String id() {
        return id;
    }

    public SemanticDimension dimension() {
        return dimension;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    /** The parent concept's id within the same taxonomy, if this concept is a specialization of another. */
    public Optional<String> parentId() {
        return parentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaxonomyConcept other)) return false;
        return id.equals(other.id) && dimension == other.dimension;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, dimension);
    }

    @Override
    public String toString() {
        return dimension + ":" + id + " (" + name + ")";
    }
}
