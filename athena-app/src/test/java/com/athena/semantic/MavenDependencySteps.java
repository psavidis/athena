package com.athena.semantic;

import com.athena.plugins.PluginRegistry;
import com.athena.reviewui.JavaFixtureSupport;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for {@code maven_dependency_changes.feature} (ticket #340), through the engine's public
 * entry point with the real plugins. Each scenario changes one module's pom.xml.
 */
public class MavenDependencySteps {

    private final PrAnalyzer analyzer = new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins());

    private Path baseRoot;
    private Path headRoot;
    private String module;
    private AnalysisResult result;

    @Before
    public void createRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-maven-base-");
        headRoot = Files.createTempDirectory("athena-maven-head-");
    }

    @After
    public void deleteRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(baseRoot);
        JavaFixtureSupport.deleteRecursively(headRoot);
    }

    @Given("a Maven module {string} whose pom gains a dependency on {string}")
    public void a_pom_gains_a_dependency(String artifactId, String coordinate) {
        module = artifactId;
        writePom(baseRoot, pom(artifactId, "", ""));
        writePom(headRoot, pom(artifactId, dependency(coordinate, null, null), ""));
    }

    @Given("a Maven module {string} whose pom loses its dependency on {string}")
    public void a_pom_loses_a_dependency(String artifactId, String coordinate) {
        module = artifactId;
        writePom(baseRoot, pom(artifactId, dependency(coordinate, null, null), ""));
        writePom(headRoot, pom(artifactId, "", ""));
    }

    @Given("a Maven module {string} whose dependency on {string} changes scope from {string} to {string}")
    public void a_dependency_changes_scope(String artifactId, String coordinate, String from, String to) {
        module = artifactId;
        writePom(baseRoot, pom(artifactId, dependency(coordinate, null, from), ""));
        writePom(headRoot, pom(artifactId, dependency(coordinate, null, to), ""));
    }

    @Given("a Maven module {string} whose managed dependency on {string} changes version from {string} to {string}")
    public void a_managed_dependency_changes_version(String artifactId, String coordinate, String from, String to) {
        module = artifactId;
        writePom(baseRoot, pom(artifactId, "", dependency(coordinate, from, null)));
        writePom(headRoot, pom(artifactId, "", dependency(coordinate, to, null)));
    }

    @Given("a Maven module {string} whose dependency on {string} drops its exclusion of {string}")
    public void a_dependency_drops_an_exclusion(String artifactId, String coordinate, String excluded) {
        module = artifactId;
        String[] parts = excluded.split(":");
        String exclusions = "      <exclusions>\n        <exclusion>\n          <groupId>" + parts[0] + "</groupId>\n          <artifactId>"
                + parts[1] + "</artifactId>\n        </exclusion>\n      </exclusions>\n    </dependency>";
        writePom(baseRoot, pom(artifactId, dependency(coordinate, null, null).replace("    </dependency>", exclusions), ""));
        writePom(headRoot, pom(artifactId, dependency(coordinate, null, null), ""));
    }

    @Given("a Maven module {string} whose pom only changes its description")
    public void a_pom_only_changes_its_description(String artifactId) {
        module = artifactId;
        writePom(baseRoot, pom(artifactId, dependency("org.slf4j:slf4j-api", null, null), "").replace("<!--d-->", "<description>old</description>"));
        writePom(headRoot, pom(artifactId, dependency("org.slf4j:slf4j-api", null, null), "").replace("<!--d-->", "<description>new</description>"));
    }

    @When("Athena analyzes the pom change")
    public void athena_analyzes_the_change() {
        result = analyzer.analyze(baseRoot, headRoot);
    }

    @Then("the change {string} is reported")
    public void the_change_is_reported(String title) {
        assertThat(result.changes()).extracting(Change::title).contains(title);
    }

    @Then("no dependency change is reported")
    public void no_dependency_change_is_reported() {
        assertThat(result.changes()).extracting(Change::kind).doesNotContainAnyElementsOf(EnumSet.of(
                TransformationKind.ADD_DEPENDENCY, TransformationKind.REMOVE_DEPENDENCY, TransformationKind.CHANGE_DEPENDENCY));
    }

    @Then("its pom.xml is listed as unrepresented with reason {string}")
    public void its_pom_is_unrepresented(String reason) {
        assertThat(result.representationCoverage().unrepresentedFiles())
                .anySatisfy(file -> {
                    assertThat(file.file().path()).isEqualTo(module + "/pom.xml");
                    assertThat(file.reason().label()).isEqualTo(reason);
                });
    }

    private void writePom(Path root, String content) {
        try {
            Path file = root.resolve(module).resolve("pom.xml");
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String pom(String artifactId, String dependencies, String managed) {
        return "<project>\n  <parent><artifactId>dropwizard-parent</artifactId></parent>\n  <artifactId>" + artifactId
                + "</artifactId>\n  <!--d-->\n"
                + (managed.isEmpty() ? "" : "  <dependencyManagement>\n    <dependencies>\n" + managed + "    </dependencies>\n  </dependencyManagement>\n")
                + "  <dependencies>\n" + dependencies + "  </dependencies>\n</project>\n";
    }

    private static String dependency(String coordinate, String version, String scope) {
        String[] parts = coordinate.split(":");
        return "    <dependency>\n      <groupId>" + parts[0] + "</groupId>\n      <artifactId>" + parts[1] + "</artifactId>\n"
                + (version == null ? "" : "      <version>" + version + "</version>\n")
                + (scope == null ? "" : "      <scope>" + scope + "</scope>\n")
                + "    </dependency>\n";
    }
}
