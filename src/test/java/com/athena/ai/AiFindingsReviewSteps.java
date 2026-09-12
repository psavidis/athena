package com.athena.ai;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class AiFindingsReviewSteps {

    private final ReviewStateStore store = new ReviewStateStore();

    private Path baseRoot;
    private Path headRoot;
    private Change renameChange;
    private String renameFindingId;
    private String generalFindingId;
    private AiFindingsBoard board;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-ai-findings-base");
        headRoot = Files.createTempDirectory("athena-ai-findings-head");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(baseRoot);
        JavaFixtureSupport.deleteRecursively(headRoot);
    }

    @Given("the reviewer has two AI findings, one about the rename Change and one with no related Change")
    public void the_reviewer_has_two_ai_findings() {
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);
        List<Change> changes = new ChangeGrouper().group(transformations);
        renameChange = changes.stream()
                .filter(change -> change.kind() == TransformationKind.RENAME_SYMBOL)
                .findFirst()
                .orElseThrow();

        renameFindingId = "finding-rename";
        generalFindingId = "finding-general";
        List<AiFinding> findings = List.of(
                new AiFinding(renameFindingId, "The renamed symbol may still be referenced in configuration files",
                        Optional.of(renameChange.title())),
                new AiFinding(generalFindingId, "Consider adding an integration test for this PR overall",
                        Optional.empty()));

        board = AiFindingsBoard.assemble(findings, changes);
    }

    @Given("the rename Change's review state is {string}")
    public void the_rename_changes_review_state_is(String state) {
        store.setState(renameChange, ReviewState.valueOf(state.toUpperCase()));
    }

    @When("the reviewer accepts the finding about the rename Change")
    public void the_reviewer_accepts_the_finding_about_the_rename_change() {
        board.accept(renameFindingId);
    }

    @When("the reviewer dismisses the finding about the rename Change")
    public void the_reviewer_dismisses_the_finding_about_the_rename_change() {
        board.dismiss(renameFindingId);
    }

    @Then("each finding item is attributed as {string}")
    public void each_finding_item_is_attributed_as(String expectedSource) {
        assertThat(board.items()).extracting(AiFindingItem::source).containsOnly(expectedSource);
    }

    @Then("that finding's disposition is {string}")
    public void that_findings_disposition_is(String expectedDisposition) {
        assertThat(board.dispositionOf(renameFindingId))
                .isEqualTo(AiFindingDisposition.valueOf(expectedDisposition.toUpperCase()));
    }

    @Then("the other finding's disposition is still {string}")
    public void the_other_findings_disposition_is_still(String expectedDisposition) {
        assertThat(board.dispositionOf(generalFindingId))
                .isEqualTo(AiFindingDisposition.valueOf(expectedDisposition.toUpperCase()));
    }

    @Then("the finding about the rename Change has a jump target to the rename Change's detail view")
    public void the_finding_about_the_rename_change_has_a_jump_target() {
        AiFindingItem item = itemFor(renameFindingId);
        assertThat(item.jumpTarget()).isPresent();
        assertThat(item.jumpTarget().get().description()).isEqualTo(renameChange.title());
    }

    @Then("the finding with no related Change has no jump target")
    public void the_finding_with_no_related_change_has_no_jump_target() {
        assertThat(itemFor(generalFindingId).jumpTarget()).isEmpty();
    }

    @Then("the rename Change's underlying review state remains {string}")
    public void the_rename_changes_underlying_review_state_remains(String expectedState) {
        assertThat(store.stateOf(renameChange)).isEqualTo(ReviewState.valueOf(expectedState.toUpperCase()));
    }

    private AiFindingItem itemFor(String findingId) {
        return board.items().stream().filter(item -> item.id().equals(findingId)).findFirst().orElseThrow();
    }
}
