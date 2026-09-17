package com.athena.reviewbriefing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dedicated unit test for {@link BriefingItem} (ticket #218). */
class BriefingItemTest {

    @Test
    void carriesAnEntityReferenceWhenGiven() {
        BriefingItem item = BriefingItem.of("The retry logic changed", "OrderService");

        assertThat(item.description()).isEqualTo("The retry logic changed");
        assertThat(item.entityReference()).contains("OrderService");
    }

    @Test
    void hasNoEntityReferenceWhenNotGiven() {
        BriefingItem item = BriefingItem.of("A repo-wide observation");

        assertThat(item.entityReference()).isEmpty();
    }

    @Test
    void treatsABlankEntityReferenceAsAbsent() {
        BriefingItem item = BriefingItem.of("A repo-wide observation", "  ");

        assertThat(item.entityReference()).isEmpty();
    }

    @Test
    void rejectsABlankDescription() {
        assertThatThrownBy(() -> BriefingItem.of("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
