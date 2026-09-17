package com.athena.reviewbriefing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Dedicated unit test for {@link UncertaintyAndQuestions} (ticket #221). */
class UncertaintyAndQuestionsTest {

    @Test
    void noneHasEmptyListsForBoth() {
        UncertaintyAndQuestions none = UncertaintyAndQuestions.none();

        assertThat(none.uncertainties()).isEmpty();
        assertThat(none.questions()).isEmpty();
    }

    @Test
    void listsAreImmutable() {
        UncertaintyAndQuestions result = new UncertaintyAndQuestions(
                List.of(BriefingItem.of("Why was this changed?")), List.of());

        assertThat(result.uncertainties()).isUnmodifiable();
    }
}
