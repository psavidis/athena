package com.athena.reviewrecorder;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dedicated unit test for {@link TranscriptSegment} (ticket #209). */
class TranscriptSegmentTest {

    private static final Instant SPOKEN_AT = Instant.parse("2026-09-17T10:41:00Z");

    @Test
    void createsASegmentWithAKnownSpeaker() {
        TranscriptSegment segment = TranscriptSegment.of("Could this execute twice?", SPOKEN_AT, "Alice");

        assertThat(segment.text()).isEqualTo("Could this execute twice?");
        assertThat(segment.spokenAt()).isEqualTo(SPOKEN_AT);
        assertThat(segment.speaker()).contains("Alice");
    }

    @Test
    void createsASegmentWithoutASpeaker() {
        TranscriptSegment segment = TranscriptSegment.withoutSpeaker("Let's get started", SPOKEN_AT);

        assertThat(segment.speaker()).isEmpty();
    }

    @Test
    void aBlankTextIsRejected() {
        assertThatThrownBy(() -> TranscriptSegment.of(" ", SPOKEN_AT, "Alice"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNullSpokenAtIsRejected() {
        assertThatThrownBy(() -> TranscriptSegment.of("Could this execute twice?", null, "Alice"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aBlankSpeakerIsRejected() {
        assertThatThrownBy(() -> TranscriptSegment.of("Could this execute twice?", SPOKEN_AT, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
