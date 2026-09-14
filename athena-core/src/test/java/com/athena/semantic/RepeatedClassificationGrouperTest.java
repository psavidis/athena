package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RepeatedClassificationGrouperTest {

    private final Taxonomy responsibilityTaxonomy = new TaxonomyLoader().load(SemanticDimension.RESPONSIBILITY);
    private final RepeatedClassificationGrouper grouper = new RepeatedClassificationGrouper();

    @Test
    void foldsTheSameConceptRecurringAcrossSeveralChangesIntoOneGroup() {
        SemanticProfile first = profileWithClassification("add-capability", "Occupancy.java");
        SemanticProfile second = profileWithClassification("add-capability", "Live.java");
        SemanticProfile third = profileWithClassification("add-capability", "Ingestion.java");

        List<RepeatedClassificationGroup> groups =
                grouper.detect(List.of(first, second, third), SemanticDimension.RESPONSIBILITY);

        assertThat(groups).hasSize(1);
        RepeatedClassificationGroup group = groups.get(0);
        assertThat(group.occurrenceCount()).isEqualTo(3);
        assertThat(group.classification().concept().id()).isEqualTo("add-capability");
    }

    @Test
    void leavesALoneClassificationUngrouped() {
        SemanticProfile lonely = profileWithClassification("add-capability", "OneOff.java");

        assertThat(grouper.detect(List.of(lonely), SemanticDimension.RESPONSIBILITY)).isEmpty();
    }

    @Test
    void keepsDifferentConceptsInSeparateGroups() {
        SemanticProfile addA = profileWithClassification("add-capability", "A.java");
        SemanticProfile addB = profileWithClassification("add-capability", "B.java");
        SemanticProfile removeA = profileWithClassification("remove-capability", "C.java");
        SemanticProfile removeB = profileWithClassification("remove-capability", "D.java");

        List<RepeatedClassificationGroup> groups =
                grouper.detect(List.of(addA, addB, removeA, removeB), SemanticDimension.RESPONSIBILITY);

        assertThat(groups).hasSize(2);
        assertThat(groups).extracting(group -> group.classification().concept().id())
                .containsExactlyInAnyOrder("add-capability", "remove-capability");
    }

    @Test
    void ignoresMoveResponsibilityClassificationsSoItNeverDoubleGroupsWithCapabilitySplitDetector() {
        SemanticProfile first = profileWithClassification("move-responsibility", "A.java");
        SemanticProfile second = profileWithClassification("move-responsibility", "B.java");

        assertThat(grouper.detect(List.of(first, second), SemanticDimension.RESPONSIBILITY)).isEmpty();
    }

    @Test
    void mergesEvidenceAndSupportingConceptNamesAcrossTheFoldedClassifications() {
        SemanticProfile first = profileWithClassificationAndSupportingConcept("add-capability", "A.java", "Device Ingestion");
        SemanticProfile second = profileWithClassificationAndSupportingConcept("add-capability", "B.java", "Live Occupancy");

        List<RepeatedClassificationGroup> groups =
                grouper.detect(List.of(first, second), SemanticDimension.RESPONSIBILITY);

        assertThat(groups).hasSize(1);
        RepeatedClassificationGroup group = groups.get(0);
        assertThat(group.classification().evidence()).hasSize(2);
        assertThat(group.classification().supportingConceptNames())
                .containsExactlyInAnyOrder("Device Ingestion", "Live Occupancy");
    }

    private SemanticProfile profileWithClassification(String conceptId, String file) {
        return profileWithClassificationAndSupportingConcept(conceptId, file, null);
    }

    private SemanticProfile profileWithClassificationAndSupportingConcept(String conceptId, String file, String supportingConceptName) {
        TaxonomyConcept concept = responsibilityTaxonomy.find(conceptId).orElseThrow();
        DetectedTransformation transformation = DetectedTransformation.of(
                TransformationKind.ADD_CLASS, List.of("Thing"), List.of(file));
        Change change = new ChangeGrouper().group(List.of(transformation)).get(0);
        List<String> supportingConceptNames = supportingConceptName == null ? List.of() : List.of(supportingConceptName);
        SemanticClassification classification =
                SemanticClassification.of(concept, List.of(transformation), supportingConceptNames);

        Map<SemanticDimension, List<SemanticClassification>> classifications = new EnumMap<>(SemanticDimension.class);
        classifications.put(SemanticDimension.RESPONSIBILITY, List.of(classification));
        return SemanticProfile.of(change, classifications);
    }
}
