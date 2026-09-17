package com.athena.reviewbriefing;

import com.athena.plugins.PluginRegistry;
import com.athena.reviewui.JavaFixtureSupport;
import com.athena.semantic.AnalysisResult;
import com.athena.semantic.Change;
import com.athena.semantic.PrAnalyzer;
import com.athena.semantic.SemanticProfile;
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
 * Step definitions for Review Briefing focus areas (ticket #220).
 * Builds real {@link Change}s/{@link SemanticProfile}s via real fixture
 * files and a real {@link PrAnalyzer} run — {@code Change} has no public
 * constructor by design. A newly-added {@code *Controller} class
 * reliably classifies under both {@code RESPONSIBILITY} and {@code
 * ARCHITECTURE} (see {@code PrAnalyzerSemanticProfileTest}), giving a
 * real, high-scoring fixture without inventing new classifier behavior;
 * a plain added class classifies under {@code STRUCTURAL} only, giving a
 * real, low-scoring one — and, unlike a rename, is guaranteed one Change
 * per class with no risk of an extra correlated "mechanical replacement"
 * Change when several files change the same way.
 */
public class FocusAreaSteps {

    private final FocusAreaGenerator generator = new FocusAreaGenerator();
    private final PrAnalyzer analyzer = new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins());

    private Path baseRoot;
    private Path headRoot;
    private AnalysisResult analysisResult;
    private List<Change> changes = List.of();
    private List<BriefingItem> generatedFocusAreas;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-focus-areas-base-");
        headRoot = Files.createTempDirectory("athena-focus-areas-head-");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(baseRoot);
        JavaFixtureSupport.deleteRecursively(headRoot);
    }

    @Given("a PR with {int} detected Changes of varying semantic significance")
    public void a_pr_with_n_detected_changes_of_varying_significance(int count) {
        // Two high-scoring (RESPONSIBILITY + ARCHITECTURE) new controllers, the rest
        // low-scoring (STRUCTURAL-only) added plain classes — "varying significance" made concrete.
        JavaFixtureSupport.write(headRoot, "UserController", "public class UserController {\n}\n");
        JavaFixtureSupport.write(headRoot, "OrderController", "public class OrderController {\n}\n");
        writeDistinctAddedClasses(count - 2);
        analyze();
    }

    @Given("a PR with a Change classified under {string} and an otherwise-equivalent Change with no such classification")
    public void a_pr_with_a_classified_change_and_an_unclassified_one(String dimension) {
        // Fixture only covers RESPONSIBILITY today (a new *Controller class reliably classifies
        // there, per PrAnalyzerSemanticProfileTest) — fail loudly rather than silently ignore a
        // different dimension this step doesn't actually know how to produce a fixture for.
        assertThat(dimension).isEqualTo("RESPONSIBILITY");
        JavaFixtureSupport.write(headRoot, "UserController", "public class UserController {\n}\n");
        writeDistinctAddedClasses(1);
        analyze();
    }

    @Given("a PR with {int} detected Changes")
    public void a_pr_with_n_detected_changes(int count) {
        writeDistinctAddedClasses(count);
        analyze();
    }

    /**
     * Writes {@code count} independent new plain classes — each its own {@code ADD_CLASS} Change,
     * reliably one-to-one (verified: unlike a same-shape rename repeated across files, which
     * PrAnalyzer additionally correlates into an extra project-wide "mechanical replacement"
     * Change on top of each individual rename — an added class carries no such risk).
     */
    private void writeDistinctAddedClasses(int count) {
        for (int i = 0; i < count; i++) {
            JavaFixtureSupport.write(headRoot, "PlainClass" + i, "public class PlainClass" + i + " {\n}\n");
        }
    }

    @Given("a PR with no detected Changes for the focus areas")
    public void a_pr_with_no_detected_changes_for_the_focus_areas() {
        JavaFixtureSupport.write(baseRoot, "Untouched", "public class Untouched {\n}\n");
        JavaFixtureSupport.write(headRoot, "Untouched", "public class Untouched {\n}\n");
        analyze();
        assertThat(changes).isEmpty();
    }

    private void analyze() {
        analysisResult = analyzer.analyze(baseRoot, headRoot);
        changes = analysisResult.changes();
    }

    @When("Athena generates the Review Briefing's focus areas")
    public void athena_generates_the_review_briefings_focus_areas() {
        List<SemanticProfile> profiles = changes.stream().map(analysisResult::semanticProfileFor).toList();
        generatedFocusAreas = generator.generate(changes, profiles);
    }

    @Then("the briefing has at most {int} focus area")
    @Then("the briefing has at most {int} focus areas")
    public void the_briefing_has_at_most_n_focus_areas(int max) {
        assertThat(generatedFocusAreas).hasSizeLessThanOrEqualTo(max);
    }

    @Then("the generated focus areas number {int}")
    public void the_generated_focus_areas_number(int count) {
        assertThat(generatedFocusAreas).hasSize(count);
    }

    @Then("the focus areas are ordered from most to least significant")
    public void the_focus_areas_are_ordered_from_most_to_least_significant() {
        assertThat(generatedFocusAreas).isNotEmpty();
        List<String> titles = generatedFocusAreas.stream().map(BriefingItem::description).toList();
        assertThat(titles.get(0)).containsAnyOf("UserController", "OrderController");
    }

    @Then("the Responsibility-classified Change's focus area ranks first")
    public void the_responsibility_classified_changes_focus_area_ranks_first() {
        assertThat(generatedFocusAreas).isNotEmpty();
        assertThat(generatedFocusAreas.get(0).entityReference()).contains("UserController");
    }

    @Then("every focus area references a semantic entity")
    public void every_focus_area_references_a_semantic_entity() {
        assertThat(generatedFocusAreas).isNotEmpty();
        assertThat(generatedFocusAreas).allSatisfy(item -> assertThat(item.entityReference()).isPresent());
    }
}
