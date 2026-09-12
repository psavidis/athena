package com.athena.semantic;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ReviewStateAndCoverageSteps {

    private final ReviewStateStore store = new ReviewStateStore();
    private final List<Change> allChanges = new ArrayList<>();

    private Change theChange;
    private Change renameChange;
    private Change moveChange;
    private Change extractionChange;
    private ReviewCoverageReport report;
    private List<Change> unreviewed;

    @Given("a newly-produced Change representing a rename")
    public void a_newly_produced_change_rename() {
        theChange = renameChange(1);
        allChanges.add(theChange);
    }

    @Given("a newly-produced Change representing a mechanical replacement")
    public void a_newly_produced_change_mechanical() {
        theChange = mechanicalChange(1);
        allChanges.add(theChange);
    }

    @Given("a Change representing a mechanical replacement with {int} occurrences")
    public void a_change_mechanical_with_occurrences(int occurrences) {
        theChange = mechanicalChange(occurrences);
        allChanges.add(theChange);
    }

    @Given("a Change representing a rename with review state {string}")
    public void a_change_rename_with_state(String state) {
        renameChange = renameChange(1);
        allChanges.add(renameChange);
        store.setState(renameChange, ReviewState.valueOf(state.toUpperCase()));
    }

    @Given("a Change representing a move with review state {string}")
    public void a_change_move_with_state(String state) {
        moveChange = moveChange();
        allChanges.add(moveChange);
        store.setState(moveChange, ReviewState.valueOf(state.toUpperCase()));
    }

    @Given("a Change representing an extraction with review state {string}")
    public void a_change_extraction_with_state(String state) {
        extractionChange = extractionChange();
        allChanges.add(extractionChange);
        store.setState(extractionChange, ReviewState.valueOf(state.toUpperCase()));
    }

    @Given("a Change representing a mechanical replacement marked mechanical")
    public void a_change_mechanical_marked_mechanical() {
        theChange = mechanicalChange(500);
        allChanges.add(theChange);
        store.markMechanical(theChange);
    }

    @When("the reviewer marks the Change as {string}")
    public void the_reviewer_marks_the_change_as(String state) {
        store.setState(theChange, ReviewState.valueOf(state.toUpperCase()));
    }

    @When("the reviewer marks the Change as mechanical")
    public void the_reviewer_marks_the_change_as_mechanical() {
        store.markMechanical(theChange);
    }

    @When("the reviewer requests review coverage")
    public void the_reviewer_requests_review_coverage() {
        report = ReviewCoverageReport.of(allChanges, store);
    }

    @When("the reviewer asks which meaningful Changes are unreviewed")
    public void the_reviewer_asks_which_changes_are_unreviewed() {
        unreviewed = ReviewCoverageReport.unreviewedChanges(allChanges, store);
    }

    @Then("the Change's review state is {string}")
    public void the_changes_review_state_is(String expectedState) {
        assertThat(store.stateOf(theChange)).isEqualTo(ReviewState.valueOf(expectedState.toUpperCase()));
    }

    @Then("the Change's review state is not {string}")
    public void the_changes_review_state_is_not(String state) {
        assertThat(store.stateOf(theChange)).isNotEqualTo(ReviewState.valueOf(state.toUpperCase()));
    }

    @Then("the Change is marked mechanical")
    public void the_change_is_marked_mechanical() {
        assertThat(store.isMechanical(theChange)).isTrue();
    }

    @Then("the coverage report shows {string} at {int} percent")
    public void the_coverage_report_shows_category_at_percent(String category, int expectedPercent) {
        assertThat(report.percentReviewed(ChangeCategory.valueOf(category.toUpperCase()))).isEqualTo(expectedPercent);
    }

    @Then("the unreviewed list includes the move Change")
    public void the_unreviewed_list_includes_the_move_change() {
        assertThat(unreviewed).contains(moveChange);
    }

    @Then("the unreviewed list includes the extraction Change")
    public void the_unreviewed_list_includes_the_extraction_change() {
        assertThat(unreviewed).contains(extractionChange);
    }

    @Then("the unreviewed list does not include the rename Change")
    public void the_unreviewed_list_does_not_include_the_rename_change() {
        assertThat(unreviewed).doesNotContain(renameChange);
    }

    private Change renameChange(int occurrences) {
        DetectedTransformation t = occurrences == 1
                ? DetectedTransformation.of(TransformationKind.RENAME_SYMBOL, List.of("Foo#m", "Bar#m"), List.of())
                : DetectedTransformation.withOccurrences(TransformationKind.RENAME_SYMBOL,
                        List.of("Foo#m", "Bar#m"), List.of(), occurrences);
        return new ChangeGrouper().group(List.of(t)).get(0);
    }

    private Change moveChange() {
        DetectedTransformation t = DetectedTransformation.of(TransformationKind.MOVE_SYMBOL,
                List.of("Foo#m", "Baz#m"), List.of());
        return new ChangeGrouper().group(List.of(t)).get(0);
    }

    private Change extractionChange() {
        DetectedTransformation t = DetectedTransformation.of(TransformationKind.EXTRACT_METHOD,
                List.of("Greeter#greet", "Greeter#buildGreeting"), List.of());
        return new ChangeGrouper().group(List.of(t)).get(0);
    }

    private Change mechanicalChange(int occurrences) {
        DetectedTransformation t = DetectedTransformation.withOccurrences(TransformationKind.MECHANICAL_REPLACEMENT,
                List.of("Foo -> Bar"), List.of(), occurrences);
        return new ChangeGrouper().group(List.of(t)).get(0);
    }
}
