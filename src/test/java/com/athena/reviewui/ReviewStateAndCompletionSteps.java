package com.athena.reviewui;

import com.athena.semantic.Change;
import com.athena.semantic.ChangeCategory;
import com.athena.semantic.ChangeGrouper;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.ReviewCoverageReport;
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
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class ReviewStateAndCompletionSteps {

    private final ReviewStateStore store = new ReviewStateStore();
    private final ReviewCompletion completion = new ReviewCompletion();

    private Path baseRoot;
    private Path headRoot;
    private List<Change> changes;
    private Change theChange;
    private ChangeMapView changeMapView;
    private String coverageSummary;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-reviewstate-base");
        headRoot = Files.createTempDirectory("athena-reviewstate-head");
    }

    @After
    public void cleanUpTempRoots() throws IOException {
        JavaFixtureSupport.deleteRecursively(baseRoot);
        JavaFixtureSupport.deleteRecursively(headRoot);
    }

    @Given("a reviewable PR with a rename Change")
    public void a_reviewable_pr_with_a_rename_change() {
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
        changes = detectChanges();
        theChange = changes.stream()
                .filter(change -> change.kind() == TransformationKind.RENAME_SYMBOL)
                .findFirst()
                .orElseThrow();
    }

    @Given("a reviewable PR with a rename Change and a mechanical replacement Change")
    public void a_reviewable_pr_with_a_rename_and_mechanical_change() {
        JavaFixtureSupport.writeRenameFixture(baseRoot, headRoot);
        JavaFixtureSupport.writeMechanicalReplacementFixture(baseRoot, headRoot);
        changes = detectChanges();
        theChange = changes.stream()
                .filter(change -> change.kind() == TransformationKind.RENAME_SYMBOL)
                .findFirst()
                .orElseThrow();
    }

    @Given("the reviewer has reviewed the rename Change")
    public void the_reviewer_has_reviewed_the_rename_change() {
        store.setState(theChange, ReviewState.REVIEWED);
    }

    @Given("the reviewer has reviewed every Change")
    public void the_reviewer_has_reviewed_every_change() {
        changes.forEach(change -> store.setState(change, ReviewState.REVIEWED));
    }

    @When("the reviewer transitions that Change to {string}")
    public void the_reviewer_transitions_that_change_to(String state) {
        store.setState(theChange, ReviewState.valueOf(state.toUpperCase()));
        changeMapView = ChangeMapView.of(changes, store);
    }

    @When("the reviewer requests the review coverage summary")
    public void the_reviewer_requests_the_review_coverage_summary() {
        coverageSummary = ReviewCoverageReport.summary(changes, store);
    }

    @When("the reviewer attempts to finish the review without confirming")
    public void the_reviewer_attempts_to_finish_the_review_without_confirming() {
        // Deliberately not calling completion.confirmCompletion(...) — this step models
        // "everything looks done" without the explicit confirming action ever happening.
    }

    @When("the reviewer confirms completing the review")
    public void the_reviewer_confirms_completing_the_review() {
        completion.confirmCompletion();
    }

    @Then("the Change Map shows that Change's review state as {string}")
    public void the_change_map_shows_that_changes_review_state_as(String expectedState) {
        ChangeMapEntry entry = changeMapView.entries().stream()
                .filter(e -> e.change().equals(theChange))
                .findFirst()
                .orElseThrow();
        assertThat(entry.reviewState()).isEqualTo(ReviewState.valueOf(expectedState.toUpperCase()));
    }

    @Then("the summary reports exactly {int} Change reviewed out of the total detected")
    public void the_summary_reports_exactly_n_reviewed_out_of_total(int reviewedCount) {
        assertThat(coverageSummary).isEqualTo(reviewedCount + " of " + changes.size() + " meaningful Changes reviewed");
    }

    @Then("the review coverage is {int} percent")
    public void the_review_coverage_is_n_percent(int expectedPercent) {
        ReviewCoverageReport report = ReviewCoverageReport.of(changes, store);
        for (ChangeCategory category : presentCategories()) {
            assertThat(report.percentReviewed(category)).isEqualTo(expectedPercent);
        }
    }

    private Set<ChangeCategory> presentCategories() {
        return changes.stream().map(change -> ChangeCategory.of(change.kind())).collect(Collectors.toSet());
    }

    @Then("the review is not marked complete")
    public void the_review_is_not_marked_complete() {
        assertThat(completion.isComplete()).isFalse();
    }

    @Then("the review is marked complete")
    public void the_review_is_marked_complete() {
        assertThat(completion.isComplete()).isTrue();
    }

    private List<Change> detectChanges() {
        List<DetectedTransformation> transformations = new TransformationDetector().detect(baseRoot, headRoot);
        return new ChangeGrouper().group(transformations);
    }
}
