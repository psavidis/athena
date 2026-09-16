package com.athena.reviewrecorder;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dedicated unit test for {@link Moment} (ticket #205). */
class MomentTest {

    private static final Instant NOW = Instant.parse("2026-09-17T10:00:00Z");

    @Test
    void createsAMomentWithItsKindReferenceAndTimestamp() {
        Moment moment = Moment.of(MomentKind.QUESTION, "entity:OrderService", NOW);

        assertThat(moment.kind()).isEqualTo(MomentKind.QUESTION);
        assertThat(moment.reference()).isEqualTo("entity:OrderService");
        assertThat(moment.taggedAt()).isEqualTo(NOW);
    }

    @Test
    void aNullKindIsRejected() {
        assertThatThrownBy(() -> Moment.of(null, "entity:OrderService", NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aNullTimestampIsRejected() {
        assertThatThrownBy(() -> Moment.of(MomentKind.QUESTION, "entity:OrderService", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aMomentWithNoActiveReferenceIsAllowed() {
        Moment moment = Moment.of(MomentKind.INSIGHT, null, NOW);

        assertThat(moment.reference()).isNull();
    }
}
