package com.athena.reviewrecorder;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dedicated unit test for {@link SemanticEvent} (ticket #204). */
class SemanticEventTest {

    private static final Instant NOW = Instant.parse("2026-09-17T10:00:00Z");

    @Test
    void createsAnEventWithItsTypeReferenceAndTimestamp() {
        SemanticEvent event = SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:OrderService", NOW);

        assertThat(event.type()).isEqualTo(SemanticEventType.ENTITY_INSPECTED);
        assertThat(event.reference()).isEqualTo("entity:OrderService");
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void aNullTypeIsRejected() {
        assertThatThrownBy(() -> SemanticEvent.of(null, "entity:OrderService", NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aBlankReferenceIsRejected() {
        assertThatThrownBy(() -> SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, " ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNullTimestampIsRejected() {
        assertThatThrownBy(() -> SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:OrderService", null))
                .isInstanceOf(NullPointerException.class);
    }
}
