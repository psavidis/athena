package com.athena.reviewcontext;

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

public class ReviewContinuitySteps {

    private final ReviewStateStore priorStore = new ReviewStateStore();

    private Path priorBaseRoot;
    private Path priorHeadRoot;
    private Path newBaseRoot;
    private Path newHeadRoot;
    private List<Change> priorChanges;
    private List<Change> newChanges;
    private Change priorRenameChange;
    private Change priorMechanicalChange;
    private ReviewContinuityReport report;
    private ReviewStateStore newStore;

    @Before
    public void createTempRoots() throws IOException {
        priorBaseRoot = Files.createTempDirectory("athena-continuity-prior-base");
        priorHeadRoot = Files.createTempDirectory("athena-continuity-prior-head");
        newBaseRoot = Files.createTempDirectory("athena-continuity-new-base");
        newHeadRoot = Files.createTempDirectory("athena-continuity-new-head");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(priorBaseRoot);
        JavaFixtureSupport.deleteRecursively(priorHeadRoot);
        JavaFixtureSupport.deleteRecursively(newBaseRoot);
        JavaFixtureSupport.deleteRecursively(newHeadRoot);
    }

    @Given("a reviewed rename Change from the prior revision")
    public void a_reviewed_rename_change_from_the_prior_revision() {
        JavaFixtureSupport.writeRenameFixture(priorBaseRoot, priorHeadRoot);
        priorChanges = detect(priorBaseRoot, priorHeadRoot);
        priorRenameChange = priorChanges.stream()
                .filter(c -> c.kind() == TransformationKind.RENAME_SYMBOL)
                .findFirst()
                .orElseThrow();
        priorStore.setState(priorRenameChange, ReviewState.REVIEWED);
    }

    @Given("a reviewed mechanical replacement Change from the prior revision with {int} occurrences")
    public void a_reviewed_mechanical_change_with_occurrences(int occurrences) {
        writeMechanicalFixture(priorBaseRoot, priorHeadRoot, occurrences);
        priorChanges = detect(priorBaseRoot, priorHeadRoot);
        priorMechanicalChange = priorChanges.stream()
                .filter(c -> c.kind() == TransformationKind.MECHANICAL_REPLACEMENT)
                .findFirst()
                .orElseThrow();
        priorStore.setState(priorMechanicalChange, ReviewState.REVIEWED);
    }

    @Given("the new revision still contains the same rename, unmodified")
    public void the_new_revision_still_contains_the_same_rename_unmodified() {
        JavaFixtureSupport.writeRenameFixture(newBaseRoot, newHeadRoot);
        newChanges = detect(newBaseRoot, newHeadRoot);
    }

    @Given("the new revision has the same mechanical replacement with {int} occurrences")
    public void the_new_revision_has_the_same_mechanical_replacement_with_occurrences(int occurrences) {
        writeMechanicalFixture(newBaseRoot, newHeadRoot, occurrences);
        newChanges = detect(newBaseRoot, newHeadRoot);
    }

    @Given("the new revision additionally introduces an unrelated move Change")
    public void the_new_revision_additionally_introduces_an_unrelated_move_change() {
        JavaFixtureSupport.writeRenameFixture(newBaseRoot, newHeadRoot);
        writeMoveFixture(newBaseRoot, newHeadRoot);
        newChanges = detect(newBaseRoot, newHeadRoot);
    }

    @Given("the new revision no longer contains that rename")
    public void the_new_revision_no_longer_contains_that_rename() {
        JavaFixtureSupport.write(newBaseRoot, "Untouched", "public class Untouched {\n}\n");
        JavaFixtureSupport.write(newHeadRoot, "Untouched", "public class Untouched {\n}\n");
        newChanges = detect(newBaseRoot, newHeadRoot);
    }

    @Given("the new revision is a force-pushed rewrite that still contains the same rename, unmodified")
    public void the_new_revision_is_a_force_pushed_rewrite_with_the_same_rename() {
        // Simulates a force-push: an entirely different temp directory pair (standing in for a
        // rewritten commit history/new SHAs) that happens to produce the same conceptual rename.
        JavaFixtureSupport.writeRenameFixture(newBaseRoot, newHeadRoot);
        newChanges = detect(newBaseRoot, newHeadRoot);
    }

    @When("continuity is computed against the new revision's Changes")
    public void continuity_is_computed_against_the_new_revisions_changes() {
        report = ReviewContinuity.compare(priorChanges, newChanges);
        newStore = report.carryForward(priorStore);
    }

    @Then("the rename Change is classified as unchanged")
    public void the_rename_change_is_classified_as_unchanged() {
        assertThat(report.unchanged()).anyMatch(pair -> pair.priorChange().equals(priorRenameChange));
    }

    @Then("the rename Change's review state carries forward as {string}")
    public void the_rename_changes_review_state_carries_forward_as(String expectedState) {
        Change newRenameChange = newChanges.stream()
                .filter(c -> c.kind() == TransformationKind.RENAME_SYMBOL)
                .findFirst()
                .orElseThrow();
        assertThat(newStore.stateOf(newRenameChange)).isEqualTo(ReviewState.valueOf(expectedState.toUpperCase()));
    }

    @Then("the mechanical replacement Change is classified as changed")
    public void the_mechanical_change_is_classified_as_changed() {
        assertThat(report.changed()).anyMatch(pair -> pair.priorChange().equals(priorMechanicalChange));
    }

    @Then("the mechanical replacement Change's review state resets to {string}")
    public void the_mechanical_changes_review_state_resets_to(String expectedState) {
        Change newMechanicalChange = newChanges.stream()
                .filter(c -> c.kind() == TransformationKind.MECHANICAL_REPLACEMENT)
                .findFirst()
                .orElseThrow();
        assertThat(newStore.stateOf(newMechanicalChange)).isEqualTo(ReviewState.valueOf(expectedState.toUpperCase()));
    }

    @Then("the move Change is classified as new")
    public void the_move_change_is_classified_as_new() {
        Change moveChange = newChanges.stream()
                .filter(c -> c.kind() == TransformationKind.MOVE_SYMBOL)
                .findFirst()
                .orElseThrow();
        assertThat(report.added()).contains(moveChange);
    }

    @Then("the move Change's review state is {string}")
    public void the_move_changes_review_state_is(String expectedState) {
        Change moveChange = newChanges.stream()
                .filter(c -> c.kind() == TransformationKind.MOVE_SYMBOL)
                .findFirst()
                .orElseThrow();
        assertThat(newStore.stateOf(moveChange)).isEqualTo(ReviewState.valueOf(expectedState.toUpperCase()));
    }

    @Then("the rename Change is classified as removed")
    public void the_rename_change_is_classified_as_removed() {
        assertThat(report.removed()).contains(priorRenameChange);
    }

    private List<Change> detect(Path baseRoot, Path headRoot) {
        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);
        return new ChangeGrouper().group(transformations);
    }

    private void writeMechanicalFixture(Path baseRoot, Path headRoot, int fileCount) {
        for (int i = 0; i < fileCount; i++) {
            String refClass = "Ref" + i;
            JavaFixtureSupport.write(baseRoot, refClass, "public class " + refClass + " {\n"
                    + "    public Foo make() {\n"
                    + "        return new Foo();\n"
                    + "    }\n"
                    + "}\n");
            JavaFixtureSupport.write(headRoot, refClass, "public class " + refClass + " {\n"
                    + "    public Bar make() {\n"
                    + "        return new Bar();\n"
                    + "    }\n"
                    + "}\n");
        }
        JavaFixtureSupport.write(baseRoot, "Foo", "public class Foo {\n}\n");
        JavaFixtureSupport.write(headRoot, "Bar", "public class Bar {\n}\n");
    }

    private void writeMoveFixture(Path baseRoot, Path headRoot) {
        JavaFixtureSupport.write(baseRoot, "Source", "public class Source {\n"
                + "    public String label() {\n"
                + "        return \"moved\";\n"
                + "    }\n"
                + "}\n");
        JavaFixtureSupport.write(baseRoot, "Destination", "public class Destination {\n}\n");
        JavaFixtureSupport.write(headRoot, "Source", "public class Source {\n}\n");
        JavaFixtureSupport.write(headRoot, "Destination", "public class Destination {\n"
                + "    public String label() {\n"
                + "        return \"moved\";\n"
                + "    }\n"
                + "}\n");
    }

}
