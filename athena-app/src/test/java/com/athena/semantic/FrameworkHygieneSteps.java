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
 * Steps for {@code framework_classification_hygiene.feature} (ticket #315), through the engine's
 * public entry point with the real language and framework plugins.
 */
public class FrameworkHygieneSteps {

    private static final String MAIN = "core/src/main/java/com/acme/";
    private static final String TEST = "core/src/test/java/com/acme/";

    private final PrAnalyzer analyzer = new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins());

    private Path baseRoot;
    private Path headRoot;
    private AnalysisResult result;

    @Before
    public void createRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-framework-base-");
        headRoot = Files.createTempDirectory("athena-framework-head-");
    }

    @After
    public void deleteRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(baseRoot);
        JavaFixtureSupport.deleteRecursively(headRoot);
    }

    @Given("a production class {string} is added annotated {string}")
    public void a_production_class_is_added(String className, String annotation) {
        JavaFixtureSupport.write(baseRoot, MAIN + "Existing", "package com.acme;\n\npublic class Existing {\n}\n");
        JavaFixtureSupport.write(headRoot, MAIN + "Existing", "package com.acme;\n\npublic class Existing {\n}\n");
        JavaFixtureSupport.write(headRoot, MAIN + className,
                "package com.acme;\n\n" + annotation + "\npublic class " + className + " {\n}\n");
    }

    @Given("a test class {string} whose nested entity gains a field annotated {string}")
    public void a_test_entity_gains_an_annotated_field(String className, String annotation) {
        String before = "package com.acme;\n\nclass " + className + " {\n    static class Library {\n    }\n}\n";
        String after = "package com.acme;\n\nclass " + className + " {\n    static class Library {\n        " + annotation
                + "\n        java.util.List<Object> books;\n    }\n}\n";
        JavaFixtureSupport.write(baseRoot, TEST + className, before);
        JavaFixtureSupport.write(headRoot, TEST + className, after);
    }

    @Given("a test class {string} gaining a method annotated {string}")
    public void a_test_class_gains_an_annotated_method(String className, String annotation) {
        JavaFixtureSupport.write(baseRoot, TEST + className, "package com.acme;\n\nclass " + className + " {\n}\n");
        JavaFixtureSupport.write(headRoot, TEST + className, "package com.acme;\n\nclass " + className + " {\n    "
                + annotation + "\n    void setUp() {\n    }\n}\n");
    }

    @When("Athena classifies the changes")
    public void athena_classifies_the_changes() {
        result = analyzer.analyze(baseRoot, headRoot);
    }

    @Then("the class's Change has no Framework classification")
    public void the_class_has_no_framework_classification() {
        assertThat(frameworkConceptNames(onlyChange())).isEmpty();
    }

    @Then("the class's Change has the Framework classification {string}")
    @Then("the method's Change has the Framework classification {string}")
    public void the_change_has_the_framework_classification(String conceptName) {
        assertThat(frameworkConceptNames(onlyChange())).containsExactly(conceptName);
    }

    @Then("no Change has a Framework classification")
    public void no_change_has_a_framework_classification() {
        assertThat(result.changes()).isNotEmpty()
                .allSatisfy(change -> assertThat(frameworkConceptNames(change)).isEmpty());
    }

    private Change onlyChange() {
        assertThat(result.changes()).hasSize(1);
        return result.changes().get(0);
    }

    private List<String> frameworkConceptNames(Change change) {
        return result.semanticProfileFor(change).classifications(SemanticDimension.FRAMEWORK).stream()
                .map(classification -> classification.concept().name()).toList();
    }
}
