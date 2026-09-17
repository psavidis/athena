package com.athena.reviewrecorder;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Dedicated unit test for {@link ReviewRecordingArtifact} (ticket #207). */
class ReviewRecordingArtifactTest {

    private static final Instant START = Instant.parse("2026-09-17T10:00:00Z");

    @Test
    void capturesTheRecordingsIdentityTimelineAndSummary() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));
        recording.capture(SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:OrderService", START));
        Moment question = recording.tagMoment(MomentKind.QUESTION);
        recording.confirmMoment(question.id());
        recording.stop();

        ReviewRecordingArtifact artifact = ReviewRecordingArtifact.of(recording);

        assertThat(artifact.recordingId()).isEqualTo(recording.id());
        assertThat(artifact.repositoryFullName()).isEqualTo("acme/widgets");
        assertThat(artifact.pullRequestNumber()).isEqualTo(42);
        assertThat(artifact.commitOrVersion()).isEqualTo("abc123");
        assertThat(artifact.events()).hasSize(1);
        assertThat(artifact.moments()).hasSize(1);
        assertThat(artifact.summary().momentCountsByKind()).containsEntry(MomentKind.QUESTION, 1);
    }

    @Test
    void capturesWhetherAudioCaptureWasEnabled() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC), true);
        recording.stop();

        ReviewRecordingArtifact artifact = ReviewRecordingArtifact.of(recording);

        assertThat(artifact.audioEnabled()).isTrue();
    }

    @Test
    void durationMatchesTheRecordingsElapsedTimeAtCaptureTime() {
        MutableClock clock = new MutableClock(START);
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros", clock);
        clock.advance(Duration.ofMinutes(5));
        recording.stop();

        ReviewRecordingArtifact artifact = ReviewRecordingArtifact.of(recording);

        assertThat(artifact.summary().duration()).isEqualTo(Duration.ofMinutes(5));
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
