package com.athena.reviewbriefing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Dedicated unit test for {@link ReviewBriefing} (ticket #218). */
class ReviewBriefingTest {

    @Test
    void groupsContentIntoNamedSections() {
        ReviewBriefing briefing = ReviewBriefing.builder()
                .changeSummary(BriefingItem.of("Renamed OrderService to OrderProcessor"))
                .addFocusArea(BriefingItem.of("The retry logic changed"))
                .addUncertainty(BriefingItem.of("Is the retry count configurable?"))
                .addQuestion(BriefingItem.of("Why was the timeout doubled?"))
                .addHistoricalContext(BriefingItem.of("This class was last touched 6 months ago"))
                .addRelevantKnowledge(BriefingItem.of("Team convention: retries use exponential backoff"))
                .recommendedStartingPoint(BriefingItem.of("Start with the retry logic"))
                .build();

        assertThat(briefing.changeSummary()).isPresent();
        assertThat(briefing.changeSummary().get().description()).isEqualTo("Renamed OrderService to OrderProcessor");
        assertThat(briefing.focusAreas()).hasSize(1);
        assertThat(briefing.uncertainties()).hasSize(1);
        assertThat(briefing.questions()).hasSize(1);
        assertThat(briefing.historicalContext()).hasSize(1);
        assertThat(briefing.relevantKnowledge()).hasSize(1);
        assertThat(briefing.recommendedStartingPoint()).isPresent();
        assertThat(briefing.recommendedStartingPoint().get().description()).isEqualTo("Start with the retry logic");
    }

    @Test
    void anEmptySectionIsPresentButEmpty() {
        ReviewBriefing briefing = ReviewBriefing.builder().build();

        assertThat(briefing.focusAreas()).isNotNull().isEmpty();
        assertThat(briefing.uncertainties()).isNotNull().isEmpty();
        assertThat(briefing.questions()).isNotNull().isEmpty();
        assertThat(briefing.historicalContext()).isNotNull().isEmpty();
        assertThat(briefing.relevantKnowledge()).isNotNull().isEmpty();
        assertThat(briefing.changeSummary()).isEmpty();
        assertThat(briefing.recommendedStartingPoint()).isEmpty();
    }

    @Test
    void twoBriefingsAreIndependentValues() {
        ReviewBriefing first = ReviewBriefing.builder().addFocusArea(BriefingItem.of("Focus 1")).build();
        ReviewBriefing second = ReviewBriefing.builder()
                .addFocusArea(BriefingItem.of("Focus A"))
                .addFocusArea(BriefingItem.of("Focus B"))
                .build();

        assertThat(first.focusAreas()).hasSize(1);
        assertThat(second.focusAreas()).hasSize(2);
    }

    @Test
    void focusAreasListIsImmutable() {
        ReviewBriefing briefing = ReviewBriefing.builder().addFocusArea(BriefingItem.of("Focus 1")).build();

        assertThat(briefing.focusAreas()).isUnmodifiable();
    }
}
