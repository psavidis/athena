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
    void audioCaptureDefaultsToDisabled() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));

        assertThat(recording.audioEnabled()).isFalse();
    }

    @Test
    void audioCaptureCanBeExplicitlyEnabled() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC), true);

        assertThat(recording.audioEnabled()).isTrue();
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

    // --- Remote multi-participant audio capture, upload, and clock sync (ticket #252) ---

    @Test
    void startedAtIsTheClockAnchoringBasisEveryParticipantReceives() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));

        // startedAt() already existed (ticket #203) — #252 repurposes it as the one clock-anchoring
        // basis every participant computes their own upload timestamps against (see
        // RemoteTranscriptMerger's own javadoc: it trusts its input streams' timestamps are
        // already comparable, which is only true once every participant anchors to the same
        // instant), rather than inventing separate new state for the same purpose.
        assertThat(recording.startedAt()).isEqualTo(START);
    }

    @Test
    void twoParticipantsUploadedTranscriptStreamsMergeInChronologicalOrder() {
        MutableClock clock = new MutableClock(START);
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros", clock, false);
        recording.join("Maria");

        recording.uploadTranscriptStream("Petros",
                List.of(TranscriptSegment.of("Could this execute twice?", START, "Petros")));
        recording.uploadTranscriptStream("Maria",
                List.of(TranscriptSegment.of("Only with a network retry.", START.plusSeconds(5), "Maria")));

        assertThat(recording.alignedTranscript()).extracting(segment -> segment.segment().speaker().orElseThrow())
                .containsExactly("Petros", "Maria");
    }

    @Test
    void aMissingParticipantStreamStillProducesAUsableTranscript() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC), false);
        recording.join("Maria");

        recording.uploadTranscriptStream("Petros",
                List.of(TranscriptSegment.of("Let's get started", START, "Petros")));
        // Maria's stream never uploads at all — must not block or corrupt the recording.

        assertThat(recording.alignedTranscript()).extracting(segment -> segment.segment().speaker().orElseThrow())
                .containsExactly("Petros");
        assertThat(recording.active()).isTrue();
    }

    @Test
    void anUploadedStreamIsMergedAlongsideTheRecordingsOwnInPersonProvider() {
        // The in-person (single-provider) and remote (per-participant upload) paths are not
        // separate code paths downstream — an uploaded stream merges into the same
        // alignedTranscript() a single TranscriptionProvider already populates, per the ticket's
        // own "no separate downstream code path" requirement.
        FakeTranscriptionProvider inPersonProvider = new FakeTranscriptionProvider(
                List.of(TranscriptSegment.of("In-person segment", START, "Petros")));
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC), false, inPersonProvider);
        recording.join("Maria");

        recording.uploadTranscriptStream("Maria",
                List.of(TranscriptSegment.of("Remote segment", START.plusSeconds(5), "Maria")));

        assertThat(recording.alignedTranscript()).extracting(segment -> segment.segment().speaker().orElseThrow())
                .containsExactly("Petros", "Maria");
    }

    @Test
    void uploadingATranscriptStreamAfterTheRecordingHasStoppedStillSucceeds() {
        // Revised while implementing #253: in-person mode uploads its captured audio only once
        // recording stops (the file isn't finished/available before then), and a remote-mode
        // participant's upload may legitimately still be in flight when someone else ends the
        // call — rejecting either case would silently drop real transcript data that arrived a
        // moment too late, which is worse than accepting it after the fact.
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC), false);
        recording.stop();

        recording.uploadTranscriptStream("Petros", List.of(TranscriptSegment.of("Finished late", START, "Petros")));

        assertThat(recording.alignedTranscript()).extracting(segment -> segment.segment().speaker().orElseThrow())
                .containsExactly("Petros");
    }

    /**
     * A {@link TranscriptionProvider} test fixture returning fixed segments (ticket #252) — the
     * same legitimate whitebox seam {@code ReviewRecordingSessionSteps.FixtureTranscriptionProvider}
     * uses (a real speech-to-text vendor is a genuine external boundary CODE_STYLE.md &sect;F
     * allows substituting).
     */
    private static final class FakeTranscriptionProvider implements TranscriptionProvider {
        private final java.util.List<TranscriptSegment> segments;

        FakeTranscriptionProvider(java.util.List<TranscriptSegment> segments) {
            this.segments = segments;
        }

        @Override
        public java.util.Optional<String> transcribe() {
            return segments.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of("(fixture transcript)");
        }

        @Override
        public java.util.List<TranscriptSegment> segments() {
            return segments;
        }
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
