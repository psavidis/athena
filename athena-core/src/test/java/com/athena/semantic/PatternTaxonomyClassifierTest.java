package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PatternTaxonomyClassifierTest {

    private final PatternTaxonomyClassifier classifier =
            new PatternTaxonomyClassifier(new TaxonomyLoader().load(SemanticDimension.PATTERN));

    @Test
    void classifiesANewlyAddedBuilderAsBuilder() {
        assertThat(classifier.classify(addClass("UserBuilder")).get().concept().id()).isEqualTo("builder");
    }

    @Test
    void classifiesANewlyAddedFactoryAsFactory() {
        assertThat(classifier.classify(addClass("UserFactory")).get().concept().id()).isEqualTo("factory");
    }

    @Test
    void classifiesANewlyAddedMapperAsMapper() {
        assertThat(classifier.classify(addClass("UserMapper")).get().concept().id()).isEqualTo("mapper");
    }

    @Test
    void classifiesANewlyAddedConverterAsMapperToo() {
        assertThat(classifier.classify(addClass("UserConverter")).get().concept().id()).isEqualTo("mapper");
    }

    @Test
    void classifiesANewlyAddedRepositoryAsRepository() {
        assertThat(classifier.classify(addClass("UserRepository")).get().concept().id()).isEqualTo("repository");
    }

    @Test
    void doesNotClassifyAMemberLevelChangeInsideAnAlreadyNamedType() {
        Optional<SemanticClassification> classification =
                classifier.classify(changeOf(TransformationKind.ADD_SYMBOL, "UserBuilder#withName"));

        assertThat(classification).isEmpty();
    }

    @Test
    void leavesAnUnrecognizedNameUnclassified() {
        assertThat(classifier.classify(addClass("Helper"))).isEmpty();
    }

    @Test
    void correlatesARemovedFieldWithAnAddedConstructorParameterOfTheSameNameAsDependencyInjection() {
        Change removedField = changeOf(TransformationKind.REMOVE_FIELD, "UserService#userRepository");
        Change addedParameter = changeOf(TransformationKind.ADD_CONSTRUCTOR_PARAMETER, "UserService#userRepository");

        Map<Change, SemanticClassification> matches =
                classifier.classifyDependencyInjection(List.of(removedField, addedParameter));

        assertThat(matches).containsOnlyKeys(addedParameter);
        assertThat(matches.get(addedParameter).concept().id()).isEqualTo("dependency-injection");
    }

    @Test
    void doesNotCorrelateAnAddedConstructorParameterWithNoMatchingRemovedField() {
        Change addedParameter = changeOf(TransformationKind.ADD_CONSTRUCTOR_PARAMETER, "UserService#userRepository");

        Map<Change, SemanticClassification> matches = classifier.classifyDependencyInjection(List.of(addedParameter));

        assertThat(matches).isEmpty();
    }

    @Test
    void doesNotCorrelateARemovedFieldFromADifferentEnclosingType() {
        Change removedField = changeOf(TransformationKind.REMOVE_FIELD, "OtherService#userRepository");
        Change addedParameter = changeOf(TransformationKind.ADD_CONSTRUCTOR_PARAMETER, "UserService#userRepository");

        Map<Change, SemanticClassification> matches =
                classifier.classifyDependencyInjection(List.of(removedField, addedParameter));

        assertThat(matches).isEmpty();
    }

    @Test
    void correlatesADroppedAutowiredAnnotationWithAnAddedConstructorParameterAsDependencyInjection() {
        Change droppedAutowired = changeWithDiff(TransformationKind.CHANGE_FIELD_ANNOTATIONS, "UserService#userRepository",
                "@Autowired\nprivate UserRepository userRepository;\n",
                "private UserRepository userRepository;\n");
        Change addedParameter = changeOf(TransformationKind.ADD_CONSTRUCTOR_PARAMETER, "UserService#userRepository");

        Map<Change, SemanticClassification> matches =
                classifier.classifyDependencyInjection(List.of(droppedAutowired, addedParameter));

        assertThat(matches).containsOnlyKeys(addedParameter);
        assertThat(matches.get(addedParameter).concept().id()).isEqualTo("dependency-injection");
    }

    @Test
    void doesNotCorrelateAFieldAnnotationChangeThatIsNotAnInjectionAnnotation() {
        Change droppedDeprecated = changeWithDiff(TransformationKind.CHANGE_FIELD_ANNOTATIONS, "UserService#userRepository",
                "@Deprecated\nprivate UserRepository userRepository;\n",
                "private UserRepository userRepository;\n");
        Change addedParameter = changeOf(TransformationKind.ADD_CONSTRUCTOR_PARAMETER, "UserService#userRepository");

        Map<Change, SemanticClassification> matches =
                classifier.classifyDependencyInjection(List.of(droppedDeprecated, addedParameter));

        assertThat(matches).isEmpty();
    }

    private Change addClass(String simpleName) {
        return changeOf(TransformationKind.ADD_CLASS, simpleName);
    }

    private Change changeOf(TransformationKind kind, String... involved) {
        DetectedTransformation t = DetectedTransformation.of(kind, List.of(involved), List.of());
        return new ChangeGrouper().group(List.of(t)).get(0);
    }

    private Change changeWithDiff(TransformationKind kind, String involved, String beforeText, String afterText) {
        DetectedTransformation t = DetectedTransformation.withDiff(kind, List.of(involved), List.of(), beforeText, afterText);
        return new ChangeGrouper().group(List.of(t)).get(0);
    }
}
