package com.athena.ai;

import com.athena.reviewcontext.ReviewContext;
import com.athena.reviewui.AnnotationBoard;
import com.athena.reviewui.JavaFixtureSupport;
import com.athena.semantic.Change;
import com.athena.semantic.ChangeGrouper;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.ReviewState;
import com.athena.semantic.ReviewStateStore;
import com.athena.semantic.TransformationDetector;
import com.athena.semantic.TransformationKind;
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

public class PostReviewAiAnalysisSteps {

    private final ReviewStateStore store = new ReviewStateStore();
    private final AnnotationBoard board = new AnnotationBoard();
    private final FakeAiProvider provider = new FakeAiProvider();

    private Path baseRoot;
    private Path headRoot;
    private Change renameChange;
    private ReviewContext reviewContext;
    private List<AiFinding> findings;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-ai-analysis-base");
        headRoot = Files.createTempDirectory("athena-ai-analysis-head");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(baseRoot);
        JavaFixtureSupport.deleteRecursively(headRoot);
    }

    @Given("the reviewer has assembled a Review Context with a reviewed rename Change and a mechanical replacement Change marked mechanical")
    public void the_reviewer_has_assembled_a_review_context() {
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
        JavaFixtureSupport.writeMechanicalReplacementFixture(baseRoot, headRoot);

        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);
        List<Change> changes = new ChangeGrouper().group(transformations);
        renameChange = changes.stream()
                .filter(change -> change.kind() == TransformationKind.RENAME_SYMBOL)
                .findFirst()
                .orElseThrow();
        Change mechanicalChange = changes.stream()
                .filter(change -> change.kind() == TransformationKind.MECHANICAL_REPLACEMENT)
                .findFirst()
                .orElseThrow();

        store.setState(renameChange, ReviewState.REVIEWED);
        store.markMechanical(mechanicalChange);

        reviewContext = ReviewContext.assemble("Move authentication to Account", changes, store, board);
    }

    @Given("the AI provider will flag a possible missed edge case")
    public void the_ai_provider_will_flag_a_possible_missed_edge_case() {
        provider.willReturnFinding("finding-1", "The renamed symbol may still be referenced in configuration files");
    }

    @Given("the AI provider has nothing to flag for this Review Context")
    public void the_ai_provider_has_nothing_to_flag() {
        // FakeAiProvider returns no findings unless one is configured via willReturnFinding —
        // this step just documents that choice for this scenario; nothing to configure.
    }

    @When("the reviewer triggers AI analysis")
    public void the_reviewer_triggers_ai_analysis() {
        findings = provider.analyze(reviewContext);
    }

    @Then("the AI provider returns candidate findings")
    public void the_ai_provider_returns_candidate_findings() {
        assertThat(findings).isNotEmpty();
    }

    @Then("each candidate finding has its own identifier")
    public void each_candidate_finding_has_its_own_identifier() {
        assertThat(findings).extracting(AiFinding::id).doesNotContainNull().doesNotHaveDuplicates();
    }

    @Then("the rename Change's review state is still {string}")
    public void the_rename_changes_review_state_is_still(String expectedState) {
        assertThat(store.stateOf(renameChange)).isEqualTo(ReviewState.valueOf(expectedState.toUpperCase()));
    }

    @Then("the AI provider returns no candidate findings")
    public void the_ai_provider_returns_no_candidate_findings() {
        assertThat(findings).isEmpty();
    }
}
