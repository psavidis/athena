package com.athena.reviewrecorder;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated unit test for {@link ReviewRecording}'s semantic event capture
 * (ticket #204) — {@code ReviewRecordingTest} already covers the
 * aggregate's session lifecycle (ticket #203); this class covers only the
 * append-oriented event stream this ticket adds.
 */
class ReviewRecordingSemanticEventsTest {

    private static final Instant START = Instant.parse("2026-09-17T10:00:00Z");

    @Test
    void capturingAnEventAppendsItToTheStream() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));

        recording.capture(SemanticEvent.of(SemanticEventType.CANVAS_NAVIGATION, "component:PaymentValidator",
                START));

        assertThat(recording.events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(SemanticEventType.CANVAS_NAVIGATION);
            assertThat(event.reference()).isEqualTo("component:PaymentValidator");
            assertThat(event.occurredAt()).isEqualTo(START);
        });
    }

    @Test
    void eventsAreListedInTheOrderTheyWereCaptured() {
        MutableClock clock = new MutableClock(START);
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros", clock);

        recording.capture(SemanticEvent.of(SemanticEventType.CANVAS_NAVIGATION, "component:PaymentValidator", clock.instant()));
        clock.advance(Duration.ofSeconds(30));
        recording.capture(SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:OrderService", clock.instant()));

        List<SemanticEvent> events = recording.events();
        assertThat(events).extracting(SemanticEvent::type)
                .containsExactly(SemanticEventType.CANVAS_NAVIGATION, SemanticEventType.ENTITY_INSPECTED);
    }

    @Test
    void capturingAnEventOnAnInactiveRecordingIsRejected() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));
        recording.stop();

        assertThatThrownBy(() -> recording.capture(SemanticEvent.of(SemanticEventType.CANVAS_NAVIGATION,
                "component:PaymentValidator", START)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void eventsStartsEmpty() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));

        assertThat(recording.events()).isEmpty();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }
}
