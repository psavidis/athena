package com.athena.semantic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every {@link TaxonomyConcept} defined for one {@link SemanticDimension}
 * (ticket #86), loaded from that dimension's JSON resource by
 * {@link TaxonomyLoader}. Extending a taxonomy means adding an entry to its
 * JSON file — never touching this class or any detection/classification
 * code (ticket #86's "extensible without requiring changes to the core
 * analysis algorithm" requirement).
 */
public final class Taxonomy {

    private final SemanticDimension dimension;
    private final Map<String, TaxonomyConcept> conceptsById;

    private Taxonomy(SemanticDimension dimension, Map<String, TaxonomyConcept> conceptsById) {
        this.dimension = dimension;
        this.conceptsById = conceptsById;
    }

    static Taxonomy of(SemanticDimension dimension, List<TaxonomyConcept> concepts) {
        Map<String, TaxonomyConcept> byId = new LinkedHashMap<>();
        for (TaxonomyConcept concept : concepts) {
            if (concept.dimension() != dimension) {
                throw new IllegalArgumentException(
                        "Concept " + concept.id() + " declares dimension " + concept.dimension()
                                + " but was loaded into " + dimension + "'s taxonomy");
            }
            if (byId.containsKey(concept.id())) {
                throw new IllegalArgumentException("Duplicate concept id in " + dimension + " taxonomy: " + concept.id());
            }
            byId.put(concept.id(), concept);
        }
        return new Taxonomy(dimension, byId);
    }

    public SemanticDimension dimension() {
        return dimension;
    }

    public List<TaxonomyConcept> concepts() {
        return List.copyOf(conceptsById.values());
    }

    public Optional<TaxonomyConcept> find(String conceptId) {
        return Optional.ofNullable(conceptsById.get(conceptId));
    }

    /** The chain from {@code conceptId} up through its ancestors, nearest first, stopping at the first concept with no parent. */
    public List<TaxonomyConcept> ancestryOf(String conceptId) {
        List<TaxonomyConcept> chain = new java.util.ArrayList<>();
        Optional<TaxonomyConcept> current = find(conceptId);
        while (current.isPresent()) {
            TaxonomyConcept concept = current.get();
            chain.add(concept);
            current = concept.parentId().flatMap(this::find);
        }
        return List.copyOf(chain);
    }
}
