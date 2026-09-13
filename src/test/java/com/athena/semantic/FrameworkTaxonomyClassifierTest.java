package com.athena.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FrameworkTaxonomyClassifierTest {

    private final FrameworkTaxonomyClassifier classifier =
            new FrameworkTaxonomyClassifier(new TaxonomyLoader().load(SemanticDimension.FRAMEWORK));

    @Test
    void classifiesANewlyAddedServiceAnnotationAsSpringService() {
        Change change = changeWithDiff(TransformationKind.ADD_SYMBOL, "UserService",
                "public class UserService {\n}\n",
                "@Service\npublic class UserService {\n}\n");

        assertThat(classifier.classify(change).get().concept().id()).isEqualTo("spring-service");
    }

    @Test
    void classifiesANewlyAddedRepositoryAnnotationAsSpringRepository() {
        Change change = changeWithDiff(TransformationKind.ADD_SYMBOL, "UserRepository",
                "public class UserRepository {\n}\n",
                "@Repository\npublic class UserRepository {\n}\n");

        assertThat(classifier.classify(change).get().concept().id()).isEqualTo("spring-repository");
    }

    @Test
    void classifiesANewlyAddedTransactionalAnnotationAsTransactionManagement() {
        Change change = changeWithDiff(TransformationKind.ADD_SYMBOL, "UserService#save",
                "public void save() { }\n",
                "@Transactional\npublic void save() { }\n");

        assertThat(classifier.classify(change).get().concept().id()).isEqualTo("spring-transaction-management");
    }

    @Test
    void classifiesANewlyAddedOneToManyAnnotationAsJpaEntityRelationship() {
        Change change = changeWithDiff(TransformationKind.ADD_FIELD, "Order#items",
                "private List<Item> items;\n",
                "@OneToMany\nprivate List<Item> items;\n");

        assertThat(classifier.classify(change).get().concept().id()).isEqualTo("jpa-entity-relationship");
    }

    @Test
    void doesNotClassifyWhenTheAnnotationWasAlreadyPresentBeforeTheChange() {
        Change change = changeWithDiff(TransformationKind.RENAME_SYMBOL, List.of("UserService#save", "UserService#persist"),
                "@Transactional\npublic void save() { }\n",
                "@Transactional\npublic void persist() { }\n");

        Optional<SemanticClassification> classification = classifier.classify(change);

        assertThat(classification).isEmpty();
    }

    @Test
    void doesNotClassifyWhenOnlyTheAnnotationsArgumentsChanged() {
        Change change = changeWithDiff(TransformationKind.CHANGE_METHOD_SIGNATURE, List.of("UserService#save"),
                "@Transactional(readOnly = true)\npublic void save() { }\n",
                "@Transactional\npublic void save() { }\n");

        Optional<SemanticClassification> classification = classifier.classify(change);

        assertThat(classification).isEmpty();
    }

    @Test
    void leavesAChangeWithNoRecognizedAnnotationUnclassified() {
        Change change = changeWithDiff(TransformationKind.ADD_SYMBOL, List.of("Greeter#greet"),
                "",
                "public String greet() { return \"hi\"; }\n");

        assertThat(classifier.classify(change)).isEmpty();
    }

    private Change changeWithDiff(TransformationKind kind, String involved, String beforeText, String afterText) {
        return changeWithDiff(kind, List.of(involved), beforeText, afterText);
    }

    private Change changeWithDiff(TransformationKind kind, List<String> involved, String beforeText, String afterText) {
        DetectedTransformation t = DetectedTransformation.withDiff(kind, involved, List.of(), beforeText, afterText);
        return new ChangeGrouper().group(List.of(t)).get(0);
    }
}
