package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaxonomyLoaderTest {

    private final TaxonomyLoader loader = new TaxonomyLoader();

    @Test
    void loadsEveryDimensionsTaxonomyFromItsJsonResource() {
        Map<SemanticDimension, Taxonomy> taxonomies = loader.loadAll();

        assertThat(taxonomies).containsOnlyKeys(SemanticDimension.values());
        for (SemanticDimension dimension : SemanticDimension.values()) {
            assertThat(taxonomies.get(dimension).dimension()).isEqualTo(dimension);
            assertThat(taxonomies.get(dimension).concepts()).isNotEmpty();
        }
    }

    @Test
    void everyLoadedConceptCarriesTheOwningDimension() {
        Taxonomy structural = loader.load(SemanticDimension.STRUCTURAL);

        assertThat(structural.concepts()).allSatisfy(concept ->
                assertThat(concept.dimension()).isEqualTo(SemanticDimension.STRUCTURAL));
    }

    @Test
    void loadsStableIdsAndDescriptionsForStructuralConcepts() {
        Taxonomy structural = loader.load(SemanticDimension.STRUCTURAL);

        assertThat(structural.find("rename")).isPresent();
        assertThat(structural.find("rename").get().name()).isEqualTo("Rename");
        assertThat(structural.find("rename").get().description()).isNotBlank();
        assertThat(structural.find("does-not-exist")).isEmpty();
    }

    @Test
    void loadsHierarchicalRelationshipsWherePresent() {
        Taxonomy pattern = loader.load(SemanticDimension.PATTERN);

        assertThat(pattern.find("factory-method").get().parentId()).contains("factory");
        assertThat(pattern.find("factory").get().parentId()).isEmpty();
    }

    @Test
    void ancestryOfWalksTheParentChainNearestFirst() {
        Taxonomy feature = loader.load(SemanticDimension.FEATURE);

        List<TaxonomyConcept> ancestry = feature.ancestryOf("user-management-add-user");

        assertThat(ancestry).extracting(TaxonomyConcept::id)
                .containsExactly("user-management-add-user", "user-management");
    }

    @Test
    void rejectsDuplicateConceptIdsWithinOneTaxonomy() {
        List<TaxonomyConcept> duplicates = List.of(
                TaxonomyConcept.of("dup", SemanticDimension.INTENT, "A", "", java.util.Optional.empty()),
                TaxonomyConcept.of("dup", SemanticDimension.INTENT, "B", "", java.util.Optional.empty()));

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> Taxonomy.of(SemanticDimension.INTENT, duplicates)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dup");
    }
}
