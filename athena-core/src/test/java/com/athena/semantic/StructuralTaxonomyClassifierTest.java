package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StructuralTaxonomyClassifierTest {

    private final StructuralTaxonomyClassifier classifier =
            new StructuralTaxonomyClassifier(new TaxonomyLoader().load(SemanticDimension.STRUCTURAL));

    @Test
    void classifiesARenameChangeAgainstTheRenameConcept() {
        Change change = changeOf(TransformationKind.RENAME_SYMBOL, "Guard#isAllowed", "Guard#isPermitted");

        Optional<SemanticClassification> classification = classifier.classify(change);

        assertThat(classification).isPresent();
        assertThat(classification.get().concept().id()).isEqualTo("rename");
        assertThat(classification.get().concept().dimension()).isEqualTo(SemanticDimension.STRUCTURAL);
    }

    @Test
    void classificationEvidenceTracesBackToTheChangesMatchedOccurrences() {
        Change change = changeOf(TransformationKind.ADD_SYMBOL, "Batch#process");

        SemanticClassification classification = classifier.classify(change).orElseThrow();

        assertThat(classification.evidence()).isEqualTo(change.matchedOccurrences());
    }

    @Test
    void classifiesEveryStructuralKindToADistinctOrSharedStableConceptId() {
        assertThat(classifier.classify(changeOf(TransformationKind.MOVE_SYMBOL, "A#m")).get().concept().id())
                .isEqualTo("move");
        assertThat(classifier.classify(changeOf(TransformationKind.REMOVE_CLASS, "A")).get().concept().id())
                .isEqualTo("remove");
        assertThat(classifier.classify(changeOf(TransformationKind.CHANGE_METHOD_SIGNATURE, "A#m")).get().concept().id())
                .isEqualTo("change-signature");
        assertThat(classifier.classify(changeOf(TransformationKind.EXTRACT_METHOD, "A#m", "A#extracted")).get().concept().id())
                .isEqualTo("extract-method");
    }

    @Test
    void classifiesContractAnnotationChangesAsTheirOwnConceptNotASignatureChange() {
        for (TransformationKind kind : List.of(TransformationKind.CHANGE_PARAMETER_ANNOTATIONS,
                TransformationKind.CHANGE_METHOD_ANNOTATIONS)) {
            Change change = new ChangeGrouper().group(List.of(
                    DetectedTransformation.of(kind, List.of("Repository#find", "+@Nullable"), List.of()))).get(0);

            assertThat(classifier.classify(change).get().concept().id()).as(kind.name())
                    .isEqualTo("change-contract-annotations");
        }
    }

    private Change changeOf(TransformationKind kind, String... involved) {
        DetectedTransformation t = DetectedTransformation.of(kind, List.of(involved), List.of());
        return new ChangeGrouper().group(List.of(t)).get(0);
    }
}
