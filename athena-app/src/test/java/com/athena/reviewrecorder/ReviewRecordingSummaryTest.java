package com.athena.reviewrecorder;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit test for {@link ReviewRecording}'s stop summary (ticket
 * #206): duration and confirmed-moment counts by kind, produced when a
 * recording stops.
 */
class ReviewRecordingSummaryTest {

    private static final Instant START = Instant.parse("2026-09-17T10:00:00Z");

    @Test
    void summaryCountsOnlyConfirmedMomentsByKind() {
        MutableClock clock = new MutableClock(START);
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros", clock);
        Moment question = recording.tagMoment(MomentKind.QUESTION);
        recording.confirmMoment(question.id());
        Moment decision = recording.tagMoment(MomentKind.DECISION);
        recording.confirmMoment(decision.id());
        recording.tagMoment(MomentKind.CONCERN); // left pending — excluded from the summary

        clock.advance(Duration.ofMinutes(10));
        recording.stop();
        ReviewRecordingSummary summary = recording.summary();

        assertThat(summary.momentCountsByKind()).containsEntry(MomentKind.QUESTION, 1);
        assertThat(summary.momentCountsByKind()).containsEntry(MomentKind.DECISION, 1);
        assertThat(summary.momentCountsByKind()).doesNotContainKey(MomentKind.CONCERN);
        assertThat(summary.duration()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void summaryWithNoConfirmedMomentsHasEmptyCounts() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));

        recording.stop();
        ReviewRecordingSummary summary = recording.summary();

        assertThat(summary.momentCountsByKind()).isEmpty();
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
