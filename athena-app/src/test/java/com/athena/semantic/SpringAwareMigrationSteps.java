package com.athena.semantic;

import com.athena.plugins.PluginRegistry;
import com.athena.reviewui.JavaFixtureSupport;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for {@code spring_aware_to_constructor_injection.feature} (ticket #294), through the
 * engine's public entry point with the real language and framework plugins.
 */
public class SpringAwareMigrationSteps {

    private final PrAnalyzer analyzer = new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins());

    private Path baseRoot;
    private Path headRoot;
    private AnalysisResult result;

    @Before
    public void createRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-aware-base-");
        headRoot = Files.createTempDirectory("athena-aware-head-");
    }

    @After
    public void deleteRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(baseRoot);
        JavaFixtureSupport.deleteRecursively(headRoot);
    }

    @Given("a class {string} whose {string} callback is replaced by a constructor receiving a(n) {string}")
    public void a_callback_replaced_by_a_constructor(String className, String setter, String type) {
        writeBase(className, setter);
        String field = fieldFor(setter);
        writeHead(className, field, "    " + className + "(" + type + " " + field + ") {\n"
                + "        this." + field + " = " + field + ";\n    }\n");
    }

    @Given("a class {string} whose {string} callback is replaced by a constructor casting the received {string}")
    public void a_callback_replaced_by_a_casting_constructor(String className, String setter, String type) {
        writeBase(className, setter);
        String field = fieldFor(setter);
        writeHead(className, field, "    " + className + "(" + type + " " + field + ") {\n"
                + "        this." + field + " = (" + typeFor(setter) + ") " + field + ";\n    }\n");
    }

    @Given("a class {string} whose {string} callback is removed with nothing replacing it")
    public void a_callback_removed(String className, String setter) {
        writeBase(className, setter);
        writeHead(className, fieldFor(setter), "");
    }

    @When("Athena analyzes the change")
    public void athena_analyzes_the_change() {
        result = analyzer.analyze(baseRoot, headRoot);
    }

    @Then("the constructor-parameter Change of {string} has the Framework classification {string}")
    public void the_constructor_parameter_change_has_the_classification(String className, String conceptName) {
        assertThat(frameworkConceptNames(constructorParameterChange(className))).containsExactly(conceptName);
    }

    @Then("that classification's evidence shows the removed setter before the added constructor parameter")
    public void the_evidence_shows_before_and_after() {
        Change change = result.changes().stream()
                .filter(c -> c.kind() == TransformationKind.ADD_CONSTRUCTOR_PARAMETER).findFirst().orElseThrow();
        SemanticClassification classification =
                result.semanticProfileFor(change).classifications(SemanticDimension.FRAMEWORK).get(0);
        assertThat(classification.beforeEvidenceCount()).isEqualTo(1);
        assertThat(classification.evidence().get(0).kind()).isEqualTo(TransformationKind.REMOVE_SYMBOL);
        assertThat(classification.evidence().get(1).kind()).isEqualTo(TransformationKind.ADD_CONSTRUCTOR_PARAMETER);
    }

    @Then("no Change of {string} has a Framework classification")
    public void no_change_has_a_framework_classification(String className) {
        assertThat(result.changes()).filteredOn(change -> change.enclosingType().equals(className))
                .allSatisfy(change -> assertThat(frameworkConceptNames(change)).isEmpty());
    }

    private Change constructorParameterChange(String className) {
        return result.changes().stream()
                .filter(change -> change.kind() == TransformationKind.ADD_CONSTRUCTOR_PARAMETER)
                .filter(change -> change.enclosingType().equals(className))
                .findFirst().orElseThrow();
    }

    private List<String> frameworkConceptNames(Change change) {
        return result.semanticProfileFor(change).classifications(SemanticDimension.FRAMEWORK).stream()
                .map(classification -> classification.concept().name()).toList();
    }

    private void writeBase(String className, String setter) {
        String field = fieldFor(setter);
        String type = typeFor(setter);
        JavaFixtureSupport.write(baseRoot, className, "public class " + className + " {\n"
                + "    private " + type + " " + field + ";\n"
                + "    public void " + setter + "(" + type + " " + field + ") {\n"
                + "        this." + field + " = " + field + ";\n    }\n}\n");
    }

    private void writeHead(String className, String field, String constructor) {
        JavaFixtureSupport.write(headRoot, className, "public class " + className + " {\n"
                + "    private final Object " + field + ";\n" + constructor + "}\n");
    }

    /** The field an Aware setter stores into: setBeanFactory -> beanFactory, setBeanClassLoader -> classLoader. */
    private static String fieldFor(String setter) {
        return setter.equals("setBeanClassLoader") ? "classLoader"
                : Character.toLowerCase(setter.charAt(3)) + setter.substring(4);
    }

    /** The type an Aware setter receives: setBeanFactory -> BeanFactory, setBeanClassLoader -> ClassLoader. */
    private static String typeFor(String setter) {
        return setter.equals("setBeanClassLoader") ? "ClassLoader" : setter.substring(3);
    }
}
