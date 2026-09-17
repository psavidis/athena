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
 * Step definitions for Review Briefing uncertainty and suggested
 * questions (ticket #221). Builds real {@link Change}s via {@code
 * JavaFixtureSupport}'s shared fixtures and a real {@link PrAnalyzer}
 * run — {@code Change} has no public constructor by design. The AI
 * reasoning pass itself is a {@link FakeUncertaintyAndQuestionsProvider}
 * — the one legitimate whitebox seam here (a real external AI-provider
 * boundary, per CODE_STYLE.md's Detroit-school exception).
 */
public class UncertaintyAndQuestionsSteps {

    private final FakeUncertaintyAndQuestionsProvider provider = new FakeUncertaintyAndQuestionsProvider();
    private final UncertaintyAndQuestionsGenerator generator = new UncertaintyAndQuestionsGenerator(provider);
    private final PrAnalyzer analyzer = new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins());

    private Path baseRoot;
    private Path headRoot;
    private AnalysisResult analysisResult;
    private List<Change> changes = List.of();
    private UncertaintyAndQuestions generatedResult;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-uncertainty-base-");
        headRoot = Files.createTempDirectory("athena-uncertainty-head-");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(baseRoot);
        JavaFixtureSupport.deleteRecursively(headRoot);
    }

    @Given("a PR with a detected Change and an AI reasoning pass that flags it as uncertain")
    public void a_pr_with_a_detected_change_and_uncertainty() {
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
        analyze();
        provider.willReturn(new UncertaintyAndQuestions(
                List.of(BriefingItem.of("Unclear why this rename was needed", "Greeter")),
                List.of(BriefingItem.of("Was this rename part of a larger refactor?", "Greeter"))));
    }

    @Given("a PR with a detected Change and an AI reasoning pass that finds nothing uncertain")
    public void a_pr_with_a_detected_change_and_no_uncertainty() {
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
        analyze();
        provider.willReturn(UncertaintyAndQuestions.none());
    }

    @Given("a PR with no detected Changes for uncertainty and questions")
    public void a_pr_with_no_detected_changes_for_uncertainty_and_questions() {
        JavaFixtureSupport.write(baseRoot, "Untouched", "public class Untouched {\n}\n");
        JavaFixtureSupport.write(headRoot, "Untouched", "public class Untouched {\n}\n");
        analyze();
        assertThat(changes).isEmpty();
    }

    private void analyze() {
        analysisResult = analyzer.analyze(baseRoot, headRoot);
        changes = analysisResult.changes();
    }

    @When("Athena generates the Review Briefing's uncertainty and questions")
    public void athena_generates_the_review_briefings_uncertainty_and_questions() {
        List<SemanticProfile> profiles = changes.stream().map(analysisResult::semanticProfileFor).toList();
        generatedResult = generator.generate(changes, profiles);
    }

    @Then("the generated uncertainties are present")
    public void the_generated_uncertainties_are_present() {
        assertThat(generatedResult.uncertainties()).isNotEmpty();
    }

    @Then("the generated questions are present")
    public void the_generated_questions_are_present() {
        assertThat(generatedResult.questions()).isNotEmpty();
    }

    @Then("the generated uncertainty count is {int}")
    public void the_generated_uncertainty_count_is(int count) {
        assertThat(generatedResult.uncertainties()).hasSize(count);
    }

    @Then("the generated question count is {int}")
    public void the_generated_question_count_is(int count) {
        assertThat(generatedResult.questions()).hasSize(count);
    }

    @Then("every generated uncertainty references a semantic entity")
    public void every_generated_uncertainty_references_a_semantic_entity() {
        assertThat(generatedResult.uncertainties()).isNotEmpty();
        assertThat(generatedResult.uncertainties())
                .allSatisfy(item -> assertThat(item.entityReference()).isPresent());
    }
}
