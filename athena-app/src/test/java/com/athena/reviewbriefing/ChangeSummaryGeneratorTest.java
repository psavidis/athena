package com.athena.reviewbriefing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit test for {@link ChangeSummaryGenerator} (ticket #219).
 * The "has real Changes" path is covered by the Cucumber scenarios
 * (which build real {@link com.athena.semantic.Change}s via a real
 * fixture repository and {@code PrAnalyzer} — {@code Change} has no
 * public constructor for a test in this package to call directly, by
 * design); this test covers the generator's own logic that doesn't
 * depend on a Change's internal shape.
 */
class ChangeSummaryGeneratorTest {

    private final FakeSemanticChangeSummaryProvider provider = new FakeSemanticChangeSummaryProvider();
    private final ChangeSummaryGenerator generator = new ChangeSummaryGenerator(provider);

    @Test
    void producesNoSummaryWhenThereAreNoChanges() {
        Optional<BriefingItem> summary = generator.generate(List.of(), List.of());

        assertThat(summary).isEmpty();
        assertThat(provider.callCount()).isZero();
    }
}
