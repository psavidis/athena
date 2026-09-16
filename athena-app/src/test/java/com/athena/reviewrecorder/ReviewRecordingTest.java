package com.athena.reviewrecorder;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated unit test for {@link ReviewRecording} (ticket #203) — the
 * central new aggregate this ticket introduces. Exercises its public API
 * directly, with no Spring/HTTP involved (that thin layer is
 * {@code ReviewRecordingController}'s own job, covered separately).
 *
 * <p>Uses a fixed {@link Clock} rather than the real system clock: a
 * genuine external/non-deterministic boundary per CODE_STYLE.md &sect;F,
 * needed to make elapsed-time assertions deterministic.
 */
class ReviewRecordingTest {

    private static final Instant START = Instant.parse("2026-09-17T10:00:00Z");

    @Test
    void startingARecordingJoinsItsStarterAsFirstParticipantAndMakesItActive() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));

        assertThat(recording.repositoryFullName()).isEqualTo("acme/widgets");
        assertThat(recording.pullRequestNumber()).isEqualTo(42);
        assertThat(recording.commitOrVersion()).isEqualTo("abc123");
        assertThat(recording.active()).isTrue();
        assertThat(recording.participantDisplayNames()).containsExactly("Petros");
        assertThat(recording.participantCount()).isEqualTo(1);
    }

    @Test
    void startingWithABlankRepositoryIsRejected() {
        assertThatThrownBy(() -> ReviewRecording.start(" ", 42, "abc123", "Petros", Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startingWithABlankStarterDisplayNameIsRejected() {
        assertThatThrownBy(() -> ReviewRecording.start("acme/widgets", 42, "abc123", " ", Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSecondParticipantCanJoinAndBothAreListed() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));

        recording.join("Maria");

        assertThat(recording.participantDisplayNames()).containsExactlyInAnyOrder("Petros", "Maria");
        assertThat(recording.participantCount()).isEqualTo(2);
    }

    @Test
    void joiningWithABlankDisplayNameIsRejected() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));

        assertThatThrownBy(() -> recording.join(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void joiningAStoppedRecordingIsRejected() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));
        recording.stop();

        assertThatThrownBy(() -> recording.join("Maria")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void elapsedTimeGrowsWhileActive() {
        MutableClock clock = new MutableClock(START);
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros", clock);

        clock.advance(Duration.ofMinutes(5));

        assertThat(recording.elapsed()).isEqualTo(Duration.ofMinutes(5));
        assertThat(recording.active()).isTrue();
    }

    @Test
    void stoppingEndsTheRecordingAndFreezesElapsedTime() {
        MutableClock clock = new MutableClock(START);
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros", clock);
        clock.advance(Duration.ofMinutes(3));

        recording.stop();
        clock.advance(Duration.ofMinutes(10));

        assertThat(recording.active()).isFalse();
        assertThat(recording.elapsed()).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    void stoppingAnAlreadyStoppedRecordingIsRejected() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));
        recording.stop();

        assertThatThrownBy(recording::stop).isInstanceOf(IllegalStateException.class);
    }

    /** A {@link Clock} whose {@link #advance} lets a test move time forward deterministically. */
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
