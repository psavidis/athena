package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RepeatedStructuralChangeGrouperTest {

    private final StructuralTaxonomyClassifier structural =
            new StructuralTaxonomyClassifier(new TaxonomyLoader().load(SemanticDimension.STRUCTURAL));
    private final RepeatedStructuralChangeGrouper grouper = new RepeatedStructuralChangeGrouper();

    @Test
    void foldsTheSameChangeOnTheSameMemberAcrossClasses() {
        List<RepeatedStructuralChange> groups = grouper.group(List.of(
                profileOf(TransformationKind.REMOVE_SYMBOL, "A#setBeanFactory"),
                profileOf(TransformationKind.REMOVE_SYMBOL, "B#setBeanFactory"),
                profileOf(TransformationKind.REMOVE_SYMBOL, "C#setBeanFactory")));

        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.concept().id()).isEqualTo("remove");
            assertThat(group.subject()).isEqualTo("setBeanFactory");
            assertThat(group.classes()).containsExactly("A", "B", "C");
            assertThat(group.mergedClassifications()).hasSize(3);
            assertThat(group.evidence()).hasSize(3);
            assertThat(group.filesTouched()).containsExactly("A.java", "B.java", "C.java");
        });
    }

    @Test
    void doesNotFoldAChangeMadeInOneClassOnly() {
        assertThat(grouper.group(List.of(
                profileOf(TransformationKind.REMOVE_SYMBOL, "A#setBeanFactory"),
                profileOf(TransformationKind.REMOVE_SYMBOL, "A#setEnvironment")))).isEmpty();
    }

    @Test
    void doesNotFoldDifferentChangesOnTheSameMemberName() {
        assertThat(grouper.group(List.of(
                profileOf(TransformationKind.REMOVE_SYMBOL, "A#setBeanFactory"),
                profileOf(TransformationKind.ADD_SYMBOL, "B#setBeanFactory")))).isEmpty();
    }

    @Test
    void neverFoldsChangesWithoutAMember() {
        assertThat(grouper.group(List.of(
                profileOf(TransformationKind.ADD_CLASS, "A"),
                profileOf(TransformationKind.ADD_CLASS, "B")))).isEmpty();
    }

    @Test
    void foldsConstructorParameterAdditionsOfTheSameName() {
        assertThat(grouper.group(List.of(
                profileOf(TransformationKind.ADD_CONSTRUCTOR_PARAMETER, "A#beanFactory"),
                profileOf(TransformationKind.ADD_CONSTRUCTOR_PARAMETER, "B#beanFactory"))))
                .singleElement()
                .satisfies(group -> assertThat(group.concept().id()).isEqualTo("add-constructor-parameter"));
    }

    @Test
    void foldsTheSameModifierChangeAcrossClassesWhateverTheMember() {
        List<RepeatedStructuralChange> groups = grouper.group(List.of(
                modifierProfile("A#assertPermitted", "+final"),
                modifierProfile("B#delegate", "+final"),
                modifierProfile("C", "+final"),
                modifierProfile("D#helper", "protected -> package-private")));

        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.concept().id()).isEqualTo("change-modifiers");
            assertThat(group.subject()).isEqualTo("+final");
            assertThat(group.classes()).containsExactly("A", "B", "C");
        });
    }

    // Ticket #363: a folded test change's JUnit lifecycle card folds with it, as in its test group (#315).

    @Test
    void foldsATestChangesFrameworkClassificationWithIt() {
        SemanticProfile first = testProfile("FirstTest#afterEachTest");
        SemanticClassification lifecycle = lifecycleOf(first);
        List<RepeatedStructuralChange> groups = grouper.group(List.of(
                first.with(SemanticDimension.FRAMEWORK, lifecycle), testProfile("SecondTest#afterEachTest")));

        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.classes()).containsExactly("FirstTest", "SecondTest");
            assertThat(group.mergedClassifications()).contains(lifecycle).hasSize(3);
        });
    }

    @Test
    void leavesAProductionChangesFrameworkClassificationOut() {
        SemanticProfile first = profileOf(TransformationKind.REMOVE_SYMBOL, "A#afterEachTest");
        SemanticClassification lifecycle = lifecycleOf(first);
        List<RepeatedStructuralChange> groups = grouper.group(List.of(
                first.with(SemanticDimension.FRAMEWORK, lifecycle), profileOf(TransformationKind.REMOVE_SYMBOL, "B#afterEachTest")));

        assertThat(groups).singleElement().satisfies(group -> assertThat(group.mergedClassifications()).doesNotContain(lifecycle));
    }

    private static SemanticClassification lifecycleOf(SemanticProfile profile) {
        TaxonomyConcept junit = TaxonomyConcept.of("junit-lifecycle", SemanticDimension.FRAMEWORK,
                "JUnit: Lifecycle/Extensions", "JUnit lifecycle annotations.", Optional.empty());
        return SemanticClassification.of(junit, profile.change().matchedOccurrences());
    }

    private SemanticProfile testProfile(String symbol) {
        String file = "core/src/test/java/" + symbol.split("#")[0] + ".java";
        Change change = new ChangeGrouper().group(List.of(DetectedTransformation.of(
                TransformationKind.REMOVE_SYMBOL, List.of(symbol), List.of(file)))).get(0);
        SemanticProfile profile = SemanticProfile.empty(change);
        return structural.classify(change).map(c -> profile.with(SemanticDimension.STRUCTURAL, c)).orElse(profile);
    }

    private SemanticProfile modifierProfile(String symbol, String delta) {
        String file = symbol.split("#")[0] + ".java";
        Change change = new ChangeGrouper().group(List.of(DetectedTransformation.of(
                TransformationKind.CHANGE_MODIFIERS, List.of(symbol, delta), List.of(file)))).get(0);
        SemanticProfile profile = SemanticProfile.empty(change);
        return structural.classify(change).map(c -> profile.with(SemanticDimension.STRUCTURAL, c)).orElse(profile);
    }

    private SemanticProfile profileOf(TransformationKind kind, String symbol) {
        String file = symbol.substring(0, symbol.contains("#") ? symbol.indexOf('#') : symbol.length()) + ".java";
        Change change = new ChangeGrouper().group(List.of(DetectedTransformation.of(kind, List.of(symbol), List.of(file)))).get(0);
        SemanticProfile profile = SemanticProfile.empty(change);
        return structural.classify(change).map(c -> profile.with(SemanticDimension.STRUCTURAL, c)).orElse(profile);
    }
}
