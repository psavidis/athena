package com.athena.reviewbriefing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit test for {@link UncertaintyAndQuestionsGenerator}
 * (ticket #221). The "has real Changes" path is covered by the Cucumber
 * scenarios (which build real {@link com.athena.semantic.Change}s via a
 * real fixture repository and {@code PrAnalyzer} run — {@code Change}
 * has no public constructor for a test in this package to call directly,
 * by design); this test covers the generator's own logic that doesn't
 * depend on a Change's internal shape.
 */
class UncertaintyAndQuestionsGeneratorTest {

    private final FakeUncertaintyAndQuestionsProvider provider = new FakeUncertaintyAndQuestionsProvider();
    private final UncertaintyAndQuestionsGenerator generator = new UncertaintyAndQuestionsGenerator(provider);

    @Test
    void producesNoUncertaintiesOrQuestionsWhenThereAreNoChanges() {
        UncertaintyAndQuestions result = generator.generate(List.of(), List.of());

        assertThat(result.uncertainties()).isNotNull().isEmpty();
        assertThat(result.questions()).isNotNull().isEmpty();
        assertThat(provider.callCount()).isZero();
    }
}
