package com.athena.reviewbriefing;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for the Review Briefing domain model (ticket #218).
 * Detroit-school: exercises the real {@link ReviewBriefing}/{@link
 * BriefingItem} public API directly — no mocks, since there's no
 * external boundary here at all.
 */
public class ReviewBriefingSteps {

    private ReviewBriefing briefing;
    private ReviewBriefing secondBriefing;
    private BriefingItem lastFocusArea;

    @Given("a Review Briefing with a change summary, one focus area, one uncertainty, one question, one piece of historical context, one piece of relevant knowledge, and a recommended starting point")
    public void a_review_briefing_with_one_of_everything() {
        briefing = ReviewBriefing.builder()
                .changeSummary(BriefingItem.of("Renamed OrderService to OrderProcessor"))
                .addFocusArea(BriefingItem.of("The retry logic changed"))
                .addUncertainty(BriefingItem.of("Is the retry count configurable?"))
                .addQuestion(BriefingItem.of("Why was the timeout doubled?"))
                .addHistoricalContext(BriefingItem.of("This class was last touched 6 months ago"))
                .addRelevantKnowledge(BriefingItem.of("Team convention: retries use exponential backoff"))
                .recommendedStartingPoint(BriefingItem.of("Start with the retry logic"))
                .build();
    }

    @Given("a Review Briefing with a focus area about the {string} entity")
    public void a_review_briefing_with_a_focus_area_about_the_entity(String entityName) {
        lastFocusArea = BriefingItem.of("Changes to " + entityName, entityName);
        briefing = ReviewBriefing.builder().addFocusArea(lastFocusArea).build();
    }

    @Given("a Review Briefing with a focus area with no entity reference")
    public void a_review_briefing_with_a_focus_area_with_no_entity_reference() {
        lastFocusArea = BriefingItem.of("A repo-wide observation");
        briefing = ReviewBriefing.builder().addFocusArea(lastFocusArea).build();
    }

    @Given("a Review Briefing with no focus areas")
    public void a_review_briefing_with_no_focus_areas() {
        briefing = ReviewBriefing.builder().build();
    }

    @Given("a Review Briefing with {int} focus area")
    @Given("a Review Briefing with {int} focus areas")
    public void a_review_briefing_with_n_focus_areas(int count) {
        briefing = briefingWithFocusAreas(count);
    }

    @Given("a second, separate Review Briefing with {int} focus areas")
    public void a_second_separate_review_briefing_with_n_focus_areas(int count) {
        secondBriefing = briefingWithFocusAreas(count);
    }

    private ReviewBriefing briefingWithFocusAreas(int count) {
        ReviewBriefing.Builder builder = ReviewBriefing.builder();
        for (int i = 0; i < count; i++) {
            builder.addFocusArea(BriefingItem.of("Focus area " + (i + 1)));
        }
        return builder.build();
    }

    @Then("the briefing's change summary is present")
    public void the_briefings_change_summary_is_present() {
        assertThat(briefing.changeSummary()).isPresent();
    }

    @Then("the briefing has {int} focus area")
    @Then("the briefing has {int} focus areas")
    public void the_briefing_has_n_focus_areas(int count) {
        assertThat(briefing.focusAreas()).hasSize(count);
    }

    @Then("the briefing has {int} uncertainty")
    @Then("the briefing has {int} uncertainties")
    public void the_briefing_has_n_uncertainties(int count) {
        assertThat(briefing.uncertainties()).hasSize(count);
    }

    @Then("the briefing has {int} question")
    @Then("the briefing has {int} questions")
    public void the_briefing_has_n_questions(int count) {
        assertThat(briefing.questions()).hasSize(count);
    }

    @Then("the briefing has {int} piece of historical context")
    @Then("the briefing has {int} pieces of historical context")
    public void the_briefing_has_n_pieces_of_historical_context(int count) {
        assertThat(briefing.historicalContext()).hasSize(count);
    }

    @Then("the briefing has {int} piece of relevant knowledge")
    @Then("the briefing has {int} pieces of relevant knowledge")
    public void the_briefing_has_n_pieces_of_relevant_knowledge(int count) {
        assertThat(briefing.relevantKnowledge()).hasSize(count);
    }

    @Then("the briefing's recommended starting point is present")
    public void the_briefings_recommended_starting_point_is_present() {
        assertThat(briefing.recommendedStartingPoint()).isPresent();
    }

    @Then("that focus area references {string}")
    public void that_focus_area_references(String entityName) {
        assertThat(lastFocusArea.entityReference()).contains(entityName);
    }

    @Then("that focus area has no entity reference")
    public void that_focus_area_has_no_entity_reference() {
        assertThat(lastFocusArea.entityReference()).isEmpty();
    }

    @Then("the briefing's focus areas section is present")
    public void the_briefings_focus_areas_section_is_present() {
        assertThat(briefing.focusAreas()).isNotNull();
    }

    @Then("the first briefing still has {int} focus area")
    public void the_first_briefing_still_has_n_focus_area(int count) {
        assertThat(briefing.focusAreas()).hasSize(count);
    }

    @Then("the second briefing has {int} focus areas")
    public void the_second_briefing_has_n_focus_areas(int count) {
        assertThat(secondBriefing.focusAreas()).hasSize(count);
    }
}
