package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ResponsibilityTaxonomyClassifierTest {

    private final ResponsibilityTaxonomyClassifier classifier =
            new ResponsibilityTaxonomyClassifier(new TaxonomyLoader().load(SemanticDimension.RESPONSIBILITY));

    @Test
    void classifiesAnAddedClassAsAddCapability() {
        assertThat(classifier.classify(changeOf(TransformationKind.ADD_CLASS, "NewThing")).get().concept().id())
                .isEqualTo("add-capability");
    }

    @Test
    void classifiesARemovedClassAsRemoveCapability() {
        assertThat(classifier.classify(changeOf(TransformationKind.REMOVE_CLASS, "OldThing")).get().concept().id())
                .isEqualTo("remove-capability");
    }

    @Test
    void classifiesAMovedSymbolAsMoveResponsibility() {
        assertThat(classifier.classify(changeOf(TransformationKind.MOVE_SYMBOL, "A#m", "B#m")).get().concept().id())
                .isEqualTo("move-responsibility");
    }

    @Test
    void classifiesAChangedSignatureAsChangeApiResponsibility() {
        assertThat(classifier.classify(changeOf(TransformationKind.CHANGE_METHOD_SIGNATURE, "A#m")).get().concept().id())
                .isEqualTo("change-api-responsibility");
    }

    @Test
    void classifiesAnExtractedMethodAsSplitResponsibility() {
        assertThat(classifier.classify(changeOf(TransformationKind.EXTRACT_METHOD, "A#m", "A#extracted")).get().concept().id())
                .isEqualTo("split-responsibility");
    }

    @Test
    void leavesARenameUnclassifiedSinceOnlyTheNameChanged() {
        Optional<SemanticClassification> classification = classifier.classify(changeOf(TransformationKind.RENAME_SYMBOL, "A#m", "A#n"));

        assertThat(classification).isEmpty();
    }

    @Test
    void leavesFormattingOnlyUnclassified() {
        assertThat(classifier.classify(changeOf(TransformationKind.FORMATTING_ONLY, "A#m"))).isEmpty();
    }

    @Test
    void leavesAMethodAddedInTestCodeUnclassifiedSinceATestIsNotACapability() {
        DetectedTransformation added = DetectedTransformation.of(TransformationKind.ADD_SYMBOL,
                List.of("GreeterTest#greetsByName"), List.of("core/src/test/java/GreeterTest.java"));

        assertThat(classifier.classify(new ChangeGrouper().group(List.of(added)).get(0))).isEmpty();
    }

    @Test
    void stillClassifiesAMethodAddedInProductionCodeAsAddCapability() {
        DetectedTransformation added = DetectedTransformation.of(TransformationKind.ADD_SYMBOL,
                List.of("Greeter#greetByName"), List.of("core/src/main/java/Greeter.java"));

        assertThat(classifier.classify(new ChangeGrouper().group(List.of(added)).get(0)).get().concept().id())
                .isEqualTo("add-capability");
    }

    @Test
    void classifiesAVisibilityChangeAsAnApiChangeButNotAKeywordOnlyChange() {
        assertThat(classifier.classify(changeOf(TransformationKind.CHANGE_MODIFIERS, "A#m", "protected -> private"))
                .get().concept().id()).isEqualTo("change-api-responsibility");
        assertThat(classifier.classify(changeOf(TransformationKind.CHANGE_MODIFIERS, "A#m", "+final"))).isEmpty();
    }

    private Change changeOf(TransformationKind kind, String... involved) {
        DetectedTransformation t = DetectedTransformation.of(kind, List.of(involved), List.of());
        return new ChangeGrouper().group(List.of(t)).get(0);
    }
}
