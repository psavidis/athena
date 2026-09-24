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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for {@code contract_annotation_structural_concept.feature} (ticket #296), through the
 * engine's public entry point. Each scenario changes one method of one class.
 */
public class ContractAnnotationSteps {

    private static final String BASE = "public class Repository {\n    public Object find(Object id) {\n        return id;\n    }\n}\n";

    private final PrAnalyzer analyzer = new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins());

    private Path baseRoot;
    private Path headRoot;
    private AnalysisResult result;

    @Before
    public void createRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-contract-base-");
        headRoot = Files.createTempDirectory("athena-contract-head-");
    }

    @After
    public void deleteRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(baseRoot);
        JavaFixtureSupport.deleteRecursively(headRoot);
    }

    @Given("a method whose parameter gains {string}")
    public void a_method_whose_parameter_gains(String annotation) {
        change("public class Repository {\n    public Object find(" + annotation + " Object id) {\n        return id;\n    }\n}\n");
    }

    @Given("a method that gains {string}")
    public void a_method_that_gains(String annotation) {
        change("public class Repository {\n    " + annotation + "\n    public Object find(Object id) {\n        return id;\n    }\n}\n");
    }

    @Given("a method that gains a parameter")
    public void a_method_that_gains_a_parameter() {
        change("public class Repository {\n    public Object find(Object id, boolean strict) {\n        return id;\n    }\n}\n");
    }

    @When("Athena classifies the change")
    public void athena_classifies_the_change() {
        result = analyzer.analyze(baseRoot, headRoot);
    }

    @Then("its Structural classification is {string}")
    public void its_structural_classification_is(String conceptName) {
        assertThat(result.changes()).singleElement().satisfies(change ->
                assertThat(result.semanticProfileFor(change).classifications(SemanticDimension.STRUCTURAL))
                        .extracting(classification -> classification.concept().name())
                        .containsExactly(conceptName));
    }

    private void change(String head) {
        JavaFixtureSupport.write(baseRoot, "Repository", BASE);
        JavaFixtureSupport.write(headRoot, "Repository", head);
    }
}
