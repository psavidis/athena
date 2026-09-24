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

    // ---- Aware callback -> constructor injection (ticket #294) ----

    @Test
    void correlatesARemovedSetBeanFactoryWithAConstructorReceivingABeanFactory() {
        Change removedSetter = changeWithDiff(TransformationKind.REMOVE_SYMBOL, "Registrar#setBeanFactory",
                "public void setBeanFactory(BeanFactory beanFactory) { this.beanFactory = beanFactory; }\n", "");
        Change addedParameter = changeWithDiff(TransformationKind.ADD_CONSTRUCTOR_PARAMETER, "Registrar#beanFactory",
                "", "Registrar(BeanFactory beanFactory) { this.beanFactory = beanFactory; }\n");

        Map<Change, SemanticClassification> matches =
                classifier.classifySpringAwareToConstructorInjection(List.of(removedSetter, addedParameter), frameworkTaxonomy);

        assertThat(matches).containsOnlyKeys(addedParameter);
        SemanticClassification classification = matches.get(addedParameter);
        assertThat(classification.concept().id()).isEqualTo("spring-aware-to-constructor-injection");
        assertThat(classification.beforeEvidenceCount()).isEqualTo(1);
        assertThat(classification.evidence()).containsExactly(removedSetter.matchedOccurrences().get(0),
                addedParameter.matchedOccurrences().get(0));
    }

    @Test
    void correlatesEachAwareCallbackWithItsOwnType() {
        Change removedSetter = changeWithDiff(TransformationKind.REMOVE_SYMBOL, "Selector#setBeanClassLoader", "x\n", "");
        Change addedParameter = changeWithDiff(TransformationKind.ADD_CONSTRUCTOR_PARAMETER, "Selector#classLoader",
                "", "Selector(ClassLoader classLoader) { this.classLoader = classLoader; }\n");

        assertThat(classifier.classifySpringAwareToConstructorInjection(List.of(removedSetter, addedParameter), frameworkTaxonomy))
                .containsOnlyKeys(addedParameter);
    }

    @Test
    void doesNotCorrelateAConstructorReceivingADifferentType() {
        Change removedSetter = changeWithDiff(TransformationKind.REMOVE_SYMBOL, "Registrar#setBeanFactory", "x\n", "");
        Change addedParameter = changeWithDiff(TransformationKind.ADD_CONSTRUCTOR_PARAMETER, "Registrar#environment",
                "", "Registrar(Environment environment) { this.environment = environment; }\n");

        assertThat(classifier.classifySpringAwareToConstructorInjection(List.of(removedSetter, addedParameter), frameworkTaxonomy))
                .isEmpty();
    }

    @Test
    void doesNotCorrelateAcrossClasses() {
        Change removedSetter = changeWithDiff(TransformationKind.REMOVE_SYMBOL, "Other#setBeanFactory", "x\n", "");
        Change addedParameter = changeWithDiff(TransformationKind.ADD_CONSTRUCTOR_PARAMETER, "Registrar#beanFactory",
                "", "Registrar(BeanFactory beanFactory) { this.beanFactory = beanFactory; }\n");

        assertThat(classifier.classifySpringAwareToConstructorInjection(List.of(removedSetter, addedParameter), frameworkTaxonomy))
                .isEmpty();
    }

    @Test
    void doesNotClassifyARemovedSetterAlone() {
        Change removedSetter = changeWithDiff(TransformationKind.REMOVE_SYMBOL, "Registrar#setBeanFactory", "x\n", "");

        assertThat(classifier.classifySpringAwareToConstructorInjection(List.of(removedSetter), frameworkTaxonomy)).isEmpty();
    }

    // ---- @ConfigurationProperties property keys (ticket #295) ----

    @Test
    void namesTheKeyOfAFieldAddedToANestedClassOfAConfigurationPropertiesClass() {
        Change added = fieldChange(TransformationKind.ADD_FIELD, "KafkaProperties.Listener#awaitAsyncResultsOnStop",
                "KafkaProperties\t@ConfigurationProperties(\"spring.kafka\")\nListener\t");

        assertThat(classifier.classify(added, frameworkTaxonomy)).get().satisfies(classification -> {
            assertThat(classification.concept().id()).isEqualTo("spring-configuration-property");
            assertThat(classification.concept().name())
                    .isEqualTo("Spring: Configuration Property spring.kafka.listener.await-async-results-on-stop");
        });
    }

    @Test
    void readsThePrefixFromThePrefixOrValueAttribute() {
        assertThat(FrameworkTaxonomyClassifier.propertyKey(
                "ServerProperties\t@ConfigurationProperties(prefix = \"server\")", "port")).contains("server.port");
        assertThat(FrameworkTaxonomyClassifier.propertyKey(
                "ServerProperties\t@ConfigurationProperties(value = \"server\")", "port")).contains("server.port");
    }

    @Test
    void usesTheInnermostConfigurationPropertiesType() {
        assertThat(FrameworkTaxonomyClassifier.propertyKey(
                "Outer\t@ConfigurationProperties(\"outer\")\nInnerProperties\t@ConfigurationProperties(\"inner\")\nDeepSettings\t",
                "maxSize")).contains("inner.deep-settings.max-size");
    }

    @Test
    void namesARemovedPropertyAsRemoved() {
        Change removed = fieldChange(TransformationKind.REMOVE_FIELD, "ServerProperties#legacyMode",
                "ServerProperties\t@ConfigurationProperties(\"server\")");

        assertThat(classifier.classify(removed, frameworkTaxonomy)).get()
                .satisfies(c -> assertThat(c.concept().name()).isEqualTo("Spring: Removed Configuration Property server.legacy-mode"));
    }

    @Test
    void leavesAFieldOfAnOrdinaryClassUnclassified() {
        Change added = fieldChange(TransformationKind.ADD_FIELD, "Settings.Listener#timeout", "Settings\t@Component\nListener\t");

        assertThat(classifier.classify(added, frameworkTaxonomy)).isEmpty();
    }

    private Change fieldChange(TransformationKind kind, String involved, String enclosingTypes) {
        DetectedTransformation t = DetectedTransformation.withDiff(kind, List.of(involved), List.of(), "", "private int x;\n")
                .withContext(DetectedTransformation.ENCLOSING_TYPE_ANNOTATIONS, enclosingTypes);
        return new ChangeGrouper().group(List.of(t)).get(0);
    }

    // ---- whole-token matching and test code (ticket #315) ----

    @Test
    void doesNotMistakeAnAnnotationThatStartsLikeAStereotypeForIt() {
        Change change = changeWithDiff(TransformationKind.ADD_CLASS, "Fixture", "", "@ServiceRegistry\npublic class Fixture { }\n");

        assertThat(classifier.classify(change, frameworkTaxonomy)).isEmpty();
    }

    @Test
    void recognizesAFullyQualifiedStereotype() {
        Change change = changeWithDiff(TransformationKind.ADD_CLASS, "OrderService", "",
                "@org.springframework.stereotype.Service\npublic class OrderService { }\n");

        assertThat(classifier.classify(change, frameworkTaxonomy)).get()
                .satisfies(c -> assertThat(c.concept().id()).isEqualTo("spring-service"));
    }

    @Test
    void leavesAJpaRelationshipInTestCodeUnclassified() {
        Change change = testChange(TransformationKind.ADD_FIELD, "LibraryTest.Library#books",
                "@OneToMany\nList<Book> books;\n");

        assertThat(classifier.classify(change, frameworkTaxonomy)).isEmpty();
    }

    @Test
    void stillClassifiesAJunitLifecycleAnnotationInTestCode() {
        Change change = testChange(TransformationKind.ADD_SYMBOL, "LibraryTest#setUp", "@BeforeEach\nvoid setUp() { }\n");

        assertThat(classifier.classify(change, frameworkTaxonomy)).get()
                .satisfies(c -> assertThat(c.concept().id()).isEqualTo("junit-lifecycle"));
    }

    private Change testChange(TransformationKind kind, String involved, String afterText) {
        DetectedTransformation t = DetectedTransformation.withDiff(kind, List.of(involved),
                List.of("core/src/test/java/LibraryTest.java"), "", afterText);
        return new ChangeGrouper().group(List.of(t)).get(0);
    }

    private Change changeWithDiff(TransformationKind kind, String involved, String beforeText, String afterText) {
        return changeWithDiff(kind, List.of(involved), beforeText, afterText);
    }

    private Change changeWithDiff(TransformationKind kind, List<String> involved, String beforeText, String afterText) {
        DetectedTransformation t = DetectedTransformation.withDiff(kind, involved, List.of(), beforeText, afterText);
        return new ChangeGrouper().group(List.of(t)).get(0);
    }
}
