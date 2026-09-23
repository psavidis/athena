package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;

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
            assertThat(group.member()).isEqualTo("setBeanFactory");
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

    private SemanticProfile profileOf(TransformationKind kind, String symbol) {
        String file = symbol.substring(0, symbol.contains("#") ? symbol.indexOf('#') : symbol.length()) + ".java";
        Change change = new ChangeGrouper().group(List.of(DetectedTransformation.of(kind, List.of(symbol), List.of(file)))).get(0);
        SemanticProfile profile = SemanticProfile.empty(change);
        return structural.classify(change).map(c -> profile.with(SemanticDimension.STRUCTURAL, c)).orElse(profile);
    }
}
