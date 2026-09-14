package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilitySplitDetectorTest {

    private final Taxonomy responsibilityTaxonomy = new TaxonomyLoader().load(SemanticDimension.RESPONSIBILITY);
    private final CapabilitySplitDetector detector = new CapabilitySplitDetector(responsibilityTaxonomy);

    @Test
    void foldsRepeatedMovesBetweenTheSameTwoModulesIntoOneGroup() {
        SemanticProfile eventStoreMove = profileWithMove(
                "connect/src/main/java/MeasurementEventStore.java", "ingestion/src/main/java/MeasurementEventStore.java");
        SemanticProfile applicationServiceMove = profileWithMove(
                "connect/src/main/java/MeasurementApplicationService.java", "ingestion/src/main/java/MeasurementApplicationService.java");

        List<CapabilitySplitGroup> groups = detector.detect(List.of(eventStoreMove, applicationServiceMove));

        assertThat(groups).hasSize(1);
        CapabilitySplitGroup group = groups.get(0);
        assertThat(group.sourceModule()).isEqualTo("connect");
        assertThat(group.destinationModule()).isEqualTo("ingestion");
        assertThat(group.moveCount()).isEqualTo(2);
        assertThat(group.classification().concept().id()).isEqualTo("capability-extraction");
    }

    @Test
    void leavesALoneMoveUngrouped() {
        SemanticProfile lonelyMove = profileWithMove(
                "connect/src/main/java/OneOffThing.java", "ingestion/src/main/java/OneOffThing.java");

        assertThat(detector.detect(List.of(lonelyMove))).isEmpty();
    }

    @Test
    void keepsMovesToDifferentDestinationModulesInSeparateGroups() {
        SemanticProfile toIngestion = profileWithMove(
                "connect/src/main/java/A.java", "ingestion/src/main/java/A.java");
        SemanticProfile alsoToIngestion = profileWithMove(
                "connect/src/main/java/B.java", "ingestion/src/main/java/B.java");
        SemanticProfile toLive = profileWithMove(
                "connect/src/main/java/C.java", "live/src/main/java/C.java");
        SemanticProfile alsoToLive = profileWithMove(
                "connect/src/main/java/D.java", "live/src/main/java/D.java");

        List<CapabilitySplitGroup> groups = detector.detect(List.of(toIngestion, alsoToIngestion, toLive, alsoToLive));

        assertThat(groups).hasSize(2);
        assertThat(groups).extracting(CapabilitySplitGroup::destinationModule)
                .containsExactlyInAnyOrder("ingestion", "live");
    }

    @Test
    void carriesTheFlowConceptNamesOfEachFoldedChangeAsSupportingConcepts() {
        SemanticProfile withFlowA = profileWithMoveAndFlow(
                "connect/src/main/java/A.java", "ingestion/src/main/java/A.java", "Device Ingestion");
        SemanticProfile withFlowB = profileWithMoveAndFlow(
                "connect/src/main/java/B.java", "ingestion/src/main/java/B.java", "Live Occupancy");

        List<CapabilitySplitGroup> groups = detector.detect(List.of(withFlowA, withFlowB));

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).classification().supportingConceptNames())
                .containsExactlyInAnyOrder("Device Ingestion", "Live Occupancy");
    }

    @Test
    void ignoresNonMoveResponsibilityClassifications() {
        TaxonomyConcept addCapability = responsibilityTaxonomy.find("add-capability").orElseThrow();
        DetectedTransformation transformation = DetectedTransformation.of(
                TransformationKind.ADD_CLASS, List.of("NewThing"), List.of("ingestion/src/main/java/NewThing.java"));
        Change change = new ChangeGrouper().group(List.of(transformation)).get(0);
        SemanticClassification classification = SemanticClassification.of(addCapability, List.of(transformation));
        SemanticProfile profile = SemanticProfile.of(change, Map.of(SemanticDimension.RESPONSIBILITY, List.of(classification)));

        assertThat(detector.detect(List.of(profile, profile))).isEmpty();
    }

    private SemanticProfile profileWithMove(String beforeFile, String afterFile) {
        return profileWithMoveAndFlow(beforeFile, afterFile, Optional.empty());
    }

    private SemanticProfile profileWithMoveAndFlow(String beforeFile, String afterFile, String flowConceptName) {
        return profileWithMoveAndFlow(beforeFile, afterFile, Optional.of(flowConceptName));
    }

    private SemanticProfile profileWithMoveAndFlow(String beforeFile, String afterFile, Optional<String> flowConceptName) {
        TaxonomyConcept moveResponsibility = responsibilityTaxonomy.find("move-responsibility").orElseThrow();
        DetectedTransformation transformation = DetectedTransformation.withDiff(
                TransformationKind.MOVE_CLASS, List.of("Thing", "Thing"), List.of(beforeFile, afterFile), "before", "after");
        Change change = new ChangeGrouper().group(List.of(transformation)).get(0);
        SemanticClassification moveClassification = SemanticClassification.of(moveResponsibility, List.of(transformation));

        Map<SemanticDimension, List<SemanticClassification>> classifications =
                new java.util.EnumMap<>(SemanticDimension.class);
        classifications.put(SemanticDimension.RESPONSIBILITY, List.of(moveClassification));
        flowConceptName.ifPresent(name -> {
            TaxonomyConcept flowConcept = TaxonomyConcept.of(
                    "flow-" + name, SemanticDimension.FEATURE, name, "", Optional.empty());
            classifications.put(SemanticDimension.FEATURE,
                    List.of(SemanticClassification.of(flowConcept, List.of(transformation))));
        });

        return SemanticProfile.of(change, classifications);
    }
}
