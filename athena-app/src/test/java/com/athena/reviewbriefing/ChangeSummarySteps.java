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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for the Review Briefing semantic change summary
 * (ticket #219). Builds a real {@link Change} via {@code
 * JavaFixtureSupport}'s shared rename fixture and a real {@link
 * PrAnalyzer} run — {@code Change} has no public constructor by design,
 * so a test outside {@code com.athena.semantic} gets one only by running
 * real analysis. Detroit-school: real {@link ChangeSummaryGenerator}
 * against a fake AI-provider boundary (the one legitimate whitebox seam
 * here).
 */
public class ChangeSummarySteps {

    private final FakeSemanticChangeSummaryProvider provider = new FakeSemanticChangeSummaryProvider();
    private final ChangeSummaryGenerator generator = new ChangeSummaryGenerator(provider);
    private final PrAnalyzer analyzer = new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins());

    private Path baseRoot;
    private Path headRoot;
    private AnalysisResult analysisResult;
    private List<Change> changes = List.of();
    private Optional<BriefingItem> generatedSummary;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-change-summary-base-");
        headRoot = Files.createTempDirectory("athena-change-summary-head-");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(baseRoot);
        JavaFixtureSupport.deleteRecursively(headRoot);
    }

    @Given("a PR with a detected Change")
    public void a_pr_with_a_detected_change() {
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
        analysisResult = analyzer.analyze(baseRoot, headRoot);
        changes = analysisResult.changes();
        assertThat(changes).isNotEmpty();
    }

    @Given("a PR with no detected Changes for the change summary")
    public void a_pr_with_no_detected_changes_for_the_change_summary() {
        JavaFixtureSupport.write(baseRoot, "Untouched", "public class Untouched {\n}\n");
        JavaFixtureSupport.write(headRoot, "Untouched", "public class Untouched {\n}\n");
        analysisResult = analyzer.analyze(baseRoot, headRoot);
        changes = analysisResult.changes();
        assertThat(changes).isEmpty();
    }

    @When("Athena generates the Review Briefing's change summary")
    public void athena_generates_the_review_briefings_change_summary() {
        List<SemanticProfile> profiles = changes.stream()
                .map(analysisResult::semanticProfileFor)
                .toList();
        generatedSummary = generator.generate(changes, profiles);
    }

    @Then("the generated change summary is present")
    public void the_generated_change_summary_is_present() {
        assertThat(generatedSummary).isPresent();
    }

    @Then("the generated change summary is absent")
    public void the_generated_change_summary_is_absent() {
        assertThat(generatedSummary).isEmpty();
    }

    @Then("the summary provider was asked about that PR's own Changes")
    public void the_summary_provider_was_asked_about_that_prs_own_changes() {
        assertThat(provider.callCount()).isEqualTo(1);
        assertThat(provider.lastSummarizedChanges()).isEqualTo(changes);
    }
}
