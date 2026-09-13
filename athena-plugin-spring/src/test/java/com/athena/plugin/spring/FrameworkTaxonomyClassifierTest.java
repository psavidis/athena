package com.athena.plugin.spring;

import com.athena.semantic.Change;
import com.athena.semantic.ChangeGrouper;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.SemanticClassification;
import com.athena.semantic.SemanticDimension;
import com.athena.semantic.Taxonomy;
import com.athena.semantic.TaxonomyLoader;
import com.athena.semantic.TransformationKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FrameworkTaxonomyClassifierTest {

    private final FrameworkTaxonomyClassifier classifier = new FrameworkTaxonomyClassifier();
    private final Taxonomy frameworkTaxonomy = new TaxonomyLoader().load(SemanticDimension.FRAMEWORK);

    @Test
    void classifiesANewlyAddedServiceAnnotationAsSpringService() {
        Change change = changeWithDiff(TransformationKind.ADD_SYMBOL, "UserService",
                "public class UserService {\n}\n",
                "@Service\npublic class UserService {\n}\n");

        assertThat(classifier.classify(change, frameworkTaxonomy).get().concept().id()).isEqualTo("spring-service");
    }

    @Test
    void classifiesANewlyAddedRepositoryAnnotationAsSpringRepository() {
        Change change = changeWithDiff(TransformationKind.ADD_SYMBOL, "UserRepository",
                "public class UserRepository {\n}\n",
                "@Repository\npublic class UserRepository {\n}\n");

        assertThat(classifier.classify(change, frameworkTaxonomy).get().concept().id()).isEqualTo("spring-repository");
    }

    @Test
    void classifiesANewlyAddedTransactionalAnnotationAsTransactionManagement() {
        Change change = changeWithDiff(TransformationKind.ADD_SYMBOL, "UserService#save",
                "public void save() { }\n",
                "@Transactional\npublic void save() { }\n");

        assertThat(classifier.classify(change, frameworkTaxonomy).get().concept().id()).isEqualTo("spring-transaction-management");
    }

    @Test
    void classifiesANewlyAddedOneToManyAnnotationAsJpaEntityRelationship() {
        Change change = changeWithDiff(TransformationKind.ADD_FIELD, "Order#items",
                "private List<Item> items;\n",
                "@OneToMany\nprivate List<Item> items;\n");

        assertThat(classifier.classify(change, frameworkTaxonomy).get().concept().id()).isEqualTo("jpa-entity-relationship");
    }

    @Test
    void doesNotClassifyWhenTheAnnotationWasAlreadyPresentBeforeTheChange() {
        Change change = changeWithDiff(TransformationKind.RENAME_SYMBOL, List.of("UserService#save", "UserService#persist"),
                "@Transactional\npublic void save() { }\n",
                "@Transactional\npublic void persist() { }\n");

        Optional<SemanticClassification> classification = classifier.classify(change, frameworkTaxonomy);

        assertThat(classification).isEmpty();
    }

    @Test
    void doesNotClassifyWhenOnlyTheAnnotationsArgumentsChanged() {
        Change change = changeWithDiff(TransformationKind.CHANGE_METHOD_SIGNATURE, List.of("UserService#save"),
                "@Transactional(readOnly = true)\npublic void save() { }\n",
                "@Transactional\npublic void save() { }\n");

        Optional<SemanticClassification> classification = classifier.classify(change, frameworkTaxonomy);

        assertThat(classification).isEmpty();
    }

    @Test
    void leavesAChangeWithNoRecognizedAnnotationUnclassified() {
        Change change = changeWithDiff(TransformationKind.ADD_SYMBOL, List.of("Greeter#greet"),
                "",
                "public String greet() { return \"hi\"; }\n");

        assertThat(classifier.classify(change, frameworkTaxonomy)).isEmpty();
    }

    @Test
    void correlatesADroppedAutowiredFieldWithAnAddedConstructorParameterAsFieldToConstructorInjection() {
        Change droppedAutowired = changeWithDiff(TransformationKind.CHANGE_FIELD_ANNOTATIONS, "UserService#userRepository",
                "@Autowired\nprivate UserRepository userRepository;\n",
                "private UserRepository userRepository;\n");
        Change addedParameter = changeWithDiff(TransformationKind.ADD_CONSTRUCTOR_PARAMETER, "UserService#userRepository",
                "public UserService() { }\n",
                "public UserService(UserRepository userRepository) { this.userRepository = userRepository; }\n");

        Map<Change, SemanticClassification> matches =
                classifier.classifySpringFieldToConstructorInjection(List.of(droppedAutowired, addedParameter), frameworkTaxonomy);

        assertThat(matches).containsOnlyKeys(addedParameter);
        assertThat(matches.get(addedParameter).concept().id()).isEqualTo("spring-field-to-constructor-injection");
    }

    @Test
    void ordersTheCorrelatedEvidenceBeforeThenAfter() {
        Change droppedAutowired = changeWithDiff(TransformationKind.CHANGE_FIELD_ANNOTATIONS, "UserService#userRepository",
                "@Autowired\nprivate UserRepository userRepository;\n",
                "private UserRepository userRepository;\n");
        Change addedParameter = changeWithDiff(TransformationKind.ADD_CONSTRUCTOR_PARAMETER, "UserService#userRepository",
                "public UserService() { }\n",
                "public UserService(UserRepository userRepository) { this.userRepository = userRepository; }\n");

        Map<Change, SemanticClassification> matches =
                classifier.classifySpringFieldToConstructorInjection(List.of(droppedAutowired, addedParameter), frameworkTaxonomy);

        SemanticClassification classification = matches.get(addedParameter);
        assertThat(classification.evidence()).hasSize(2);
        assertThat(classification.evidence().get(0)).isSameAs(droppedAutowired.matchedOccurrences().get(0));
        assertThat(classification.evidence().get(1)).isSameAs(addedParameter.matchedOccurrences().get(0));
    }

    @Test
    void doesNotCorrelateAnAddedConstructorParameterWithNoMatchingFieldChange() {
        Change addedParameter = changeWithDiff(TransformationKind.ADD_CONSTRUCTOR_PARAMETER, "UserService#userRepository",
                "public UserService() { }\n",
                "public UserService(UserRepository userRepository) { this.userRepository = userRepository; }\n");

        Map<Change, SemanticClassification> matches =
                classifier.classifySpringFieldToConstructorInjection(List.of(addedParameter), frameworkTaxonomy);

        assertThat(matches).isEmpty();
    }

    @Test
    void doesNotCorrelateAFieldAnnotationChangeThatIsNotAnInjectionAnnotation() {
        Change droppedDeprecated = changeWithDiff(TransformationKind.CHANGE_FIELD_ANNOTATIONS, "UserService#userRepository",
                "@Deprecated\nprivate UserRepository userRepository;\n",
                "private UserRepository userRepository;\n");
        Change addedParameter = changeWithDiff(TransformationKind.ADD_CONSTRUCTOR_PARAMETER, "UserService#userRepository",
                "public UserService() { }\n",
                "public UserService(UserRepository userRepository) { this.userRepository = userRepository; }\n");

        Map<Change, SemanticClassification> matches =
                classifier.classifySpringFieldToConstructorInjection(List.of(droppedDeprecated, addedParameter), frameworkTaxonomy);

        assertThat(matches).isEmpty();
    }

    @Test
    void doesNotCorrelateAFieldChangeFromADifferentEnclosingType() {
        Change droppedAutowired = changeWithDiff(TransformationKind.CHANGE_FIELD_ANNOTATIONS, "OtherService#userRepository",
                "@Autowired\nprivate UserRepository userRepository;\n",
                "private UserRepository userRepository;\n");
        Change addedParameter = changeWithDiff(TransformationKind.ADD_CONSTRUCTOR_PARAMETER, "UserService#userRepository",
                "public UserService() { }\n",
                "public UserService(UserRepository userRepository) { this.userRepository = userRepository; }\n");

        Map<Change, SemanticClassification> matches =
                classifier.classifySpringFieldToConstructorInjection(List.of(droppedAutowired, addedParameter), frameworkTaxonomy);

        assertThat(matches).isEmpty();
    }

    private Change changeWithDiff(TransformationKind kind, String involved, String beforeText, String afterText) {
        return changeWithDiff(kind, List.of(involved), beforeText, afterText);
    }

    private Change changeWithDiff(TransformationKind kind, List<String> involved, String beforeText, String afterText) {
        DetectedTransformation t = DetectedTransformation.withDiff(kind, involved, List.of(), beforeText, afterText);
        return new ChangeGrouper().group(List.of(t)).get(0);
    }
}
