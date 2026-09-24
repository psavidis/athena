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
 * Steps for {@code spring_configuration_property_keys.feature} (ticket #295), through the
 * engine's public entry point with the real language and framework plugins. The fixture is an
 * outer class (optionally {@code @ConfigurationProperties}) with one nested class; every type
 * starts with a {@code legacyMode} field so a scenario can remove it.
 */
public class ConfigurationPropertySteps {

    private final PrAnalyzer analyzer = new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins());

    private Path baseRoot;
    private Path headRoot;
    private String outerClass;
    private String annotation;
    private String nestedClass;
    private AnalysisResult result;

    @Before
    public void createRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-config-base-");
        headRoot = Files.createTempDirectory("athena-config-head-");
    }

    @After
    public void deleteRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(baseRoot);
        JavaFixtureSupport.deleteRecursively(headRoot);
    }

    @Given("a @ConfigurationProperties class {string} with prefix {string} and a nested class {string}")
    public void a_configuration_properties_class(String outer, String prefix, String nested) {
        define(outer, "@ConfigurationProperties(\"" + prefix + "\")\n", nested);
    }

    @Given("a @ConfigurationProperties class {string} with prefix attribute {string} and a nested class {string}")
    public void a_configuration_properties_class_with_prefix_attribute(String outer, String prefix, String nested) {
        define(outer, "@ConfigurationProperties(prefix = \"" + prefix + "\")\n", nested);
    }

    @Given("an ordinary class {string} with a nested class {string}")
    public void an_ordinary_class(String outer, String nested) {
        define(outer, "", nested);
    }

    @When("field {string} is added to {string}")
    public void a_field_is_added(String field, String type) {
        JavaFixtureSupport.write(baseRoot, outerClass, source("", ""));
        JavaFixtureSupport.write(headRoot, outerClass, type.equals(outerClass) ? source(field, "") : source("", field));
        result = analyzer.analyze(baseRoot, headRoot);
    }

    @When("field {string} is removed from {string}")
    public void a_field_is_removed(String field, String type) {
        assertThat(field).isEqualTo("legacyMode");
        JavaFixtureSupport.write(baseRoot, outerClass, source("", ""));
        String withoutLegacyMode = source("", "").replaceFirst(
                "(static class " + nestedClass + " \\{\n)        private boolean legacyMode;\n", "$1");
        JavaFixtureSupport.write(headRoot, outerClass, withoutLegacyMode);
        result = analyzer.analyze(baseRoot, headRoot);
    }

    @Then("the added field's Framework classification is {string}")
    public void the_added_fields_classification_is(String conceptName) {
        assertThat(frameworkConceptNames(TransformationKind.ADD_FIELD)).containsExactly(conceptName);
    }

    @Then("the removed field's Framework classification is {string}")
    public void the_removed_fields_classification_is(String conceptName) {
        assertThat(frameworkConceptNames(TransformationKind.REMOVE_FIELD)).containsExactly(conceptName);
    }

    @Then("the added field has no Framework classification")
    public void the_added_field_has_no_classification() {
        assertThat(frameworkConceptNames(TransformationKind.ADD_FIELD)).isEmpty();
    }

    private void define(String outer, String annotationText, String nested) {
        outerClass = outer;
        annotation = annotationText;
        nestedClass = nested;
    }

    private List<String> frameworkConceptNames(TransformationKind kind) {
        Change change = result.changes().stream().filter(c -> c.kind() == kind).findFirst().orElseThrow();
        return result.semanticProfileFor(change).classifications(SemanticDimension.FRAMEWORK).stream()
                .map(classification -> classification.concept().name()).toList();
    }

    /** The outer class, with {@code outerField}/{@code nestedField} added when not empty. */
    private String source(String outerField, String nestedField) {
        return annotation + "public class " + outerClass + " {\n"
                + "    private boolean legacyMode;\n"
                + (outerField.isEmpty() ? "" : "    private int " + outerField + ";\n")
                + "    public static class " + nestedClass + " {\n"
                + "        private boolean legacyMode;\n"
                + (nestedField.isEmpty() ? "" : "        private boolean " + nestedField + ";\n")
                + "    }\n}\n";
    }
}
