package com.athena.reviewrecorder;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated unit test for {@link ReviewRecording}'s explicit moment
 * tagging (ticket #205) — {@code ReviewRecordingSemanticEventsTest}
 * already covers the event stream (ticket #204) this ticket references.
 */
class ReviewRecordingMomentTaggingTest {

    private static final Instant START = Instant.parse("2026-09-17T10:00:00Z");

    @Test
    void taggingAMomentReferencesTheMostRecentlyCapturedEvent() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));
        recording.capture(SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:OrderService", START));

        recording.tagMoment(MomentKind.QUESTION);

        assertThat(recording.moments()).singleElement().satisfies(moment -> {
            assertThat(moment.kind()).isEqualTo(MomentKind.QUESTION);
            assertThat(moment.reference()).isEqualTo("entity:OrderService");
        });
    }

    @Test
    void taggingAMomentWithNoEventsCapturedYetHasNoReference() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));

        recording.tagMoment(MomentKind.INSIGHT);

        assertThat(recording.moments()).singleElement().satisfies(moment ->
                assertThat(moment.reference()).isNull());
    }

    @Test
    void momentsAreListedInTheOrderTheyWereTagged() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));
        recording.capture(SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:OrderService", START));
        recording.tagMoment(MomentKind.QUESTION);
        recording.capture(SemanticEvent.of(SemanticEventType.CANVAS_NAVIGATION, "component:PaymentValidator", START));
        recording.tagMoment(MomentKind.DECISION);

        List<Moment> moments = recording.moments();

        assertThat(moments).extracting(Moment::kind).containsExactly(MomentKind.QUESTION, MomentKind.DECISION);
    }

    @Test
    void taggingAMomentOnAnInactiveRecordingIsRejected() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));
        recording.stop();

        assertThatThrownBy(() -> recording.tagMoment(MomentKind.QUESTION)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void momentsStartsEmpty() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));

        assertThat(recording.moments()).isEmpty();
    }
}
