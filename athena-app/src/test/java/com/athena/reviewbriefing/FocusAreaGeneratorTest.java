package com.athena.reviewbriefing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit test for {@link FocusAreaGenerator} (ticket #220). The
 * ranking logic itself (weighting, ordering, capping at 4) is covered by
 * the Cucumber scenarios, which build real {@link
 * com.athena.semantic.Change}s/{@link com.athena.semantic.SemanticProfile}s
 * via a real fixture repository and {@code PrAnalyzer} run — {@code
 * Change} has no public constructor for a test in this package to call
 * directly, by design. This test covers the generator's own logic that
 * doesn't depend on a Change's internal shape.
 */
class FocusAreaGeneratorTest {

    private final FocusAreaGenerator generator = new FocusAreaGenerator();

    @Test
    void producesNoFocusAreasWhenThereAreNoChanges() {
        List<BriefingItem> focusAreas = generator.generate(List.of(), List.of());

        assertThat(focusAreas).isNotNull().isEmpty();
    }
}
