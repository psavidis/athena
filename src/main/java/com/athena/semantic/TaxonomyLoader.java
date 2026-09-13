package com.athena.semantic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads every {@link SemanticDimension}'s taxonomy from its JSON resource
 * (ticket #86). Each resource is a JSON array of concept objects:
 * {@code {"id": "...", "name": "...", "description": "...", "parentId": "..."}}
 * ({@code parentId} optional). This is the taxonomy's entire schema —
 * extending a taxonomy is editing its JSON file, never this class.
 */
public final class TaxonomyLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Loads every dimension's taxonomy from its declared classpath resource. */
    public Map<SemanticDimension, Taxonomy> loadAll() {
        Map<SemanticDimension, Taxonomy> taxonomies = new EnumMap<>(SemanticDimension.class);
        for (SemanticDimension dimension : SemanticDimension.values()) {
            taxonomies.put(dimension, load(dimension));
        }
        return taxonomies;
    }

    public Taxonomy load(SemanticDimension dimension) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(dimension.taxonomyResource())) {
            if (stream == null) {
                throw new IllegalStateException("Missing taxonomy resource: " + dimension.taxonomyResource());
            }
            JsonNode root = objectMapper.readTree(stream);
            List<TaxonomyConcept> concepts = new ArrayList<>();
            for (JsonNode node : root) {
                concepts.add(toConcept(dimension, node));
            }
            return Taxonomy.of(dimension, concepts);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load taxonomy resource: " + dimension.taxonomyResource(), e);
        }
    }

    private TaxonomyConcept toConcept(SemanticDimension dimension, JsonNode node) {
        String id = requireText(node, "id", dimension);
        String name = requireText(node, "name", dimension);
        String description = node.hasNonNull("description") ? node.get("description").asText() : "";
        Optional<String> parentId = node.hasNonNull("parentId")
                ? Optional.of(node.get("parentId").asText())
                : Optional.empty();
        return TaxonomyConcept.of(id, dimension, name, description, parentId);
    }

    private String requireText(JsonNode node, String field, SemanticDimension dimension) {
        if (!node.hasNonNull(field)) {
            throw new IllegalStateException(
                    "Taxonomy entry in " + dimension.taxonomyResource() + " is missing required field \"" + field + "\"");
        }
        return node.get(field).asText();
    }
}
