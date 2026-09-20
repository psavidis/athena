package com.athena.reviewrecorder;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit test for {@link ReviewRecording#alignedTranscript()}
 * (ticket #209) — {@link TranscriptAlignerTest} already covers the
 * alignment algorithm itself; this class covers only the recording
 * wiring it (its own held {@link TranscriptionProvider} and event
 * stream) into that algorithm.
 */
class ReviewRecordingTranscriptAlignmentTest {

    private static final Instant START = Instant.parse("2026-09-17T10:00:00Z");

    @Test
    void alignsSegmentsFromItsTranscriptionProviderAgainstItsOwnEvents() {
        StubTranscriptionProvider provider = new StubTranscriptionProvider(
                List.of(TranscriptSegment.of("Could this execute twice?", START.plusSeconds(120), "Alice")));
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC), false, provider);
        recording.capture(SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:PaymentProcessor", START.plusSeconds(60)));

        List<AlignedTranscriptSegment> aligned = recording.alignedTranscript();

        assertThat(aligned).singleElement().satisfies(segment ->
                assertThat(segment.entityReference()).contains("entity:PaymentProcessor"));
    }

    @Test
    void isAlwaysEmptyWithTheDefaultNoOpTranscriptionProvider() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));
        recording.capture(SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:PaymentProcessor", START));

        assertThat(recording.alignedTranscript()).isEmpty();
    }

    private static final class StubTranscriptionProvider implements TranscriptionProvider {
        private final List<TranscriptSegment> segments;

        StubTranscriptionProvider(List<TranscriptSegment> segments) {
            this.segments = segments;
        }

        @Override
        public Optional<String> transcribe() {
            return segments.isEmpty() ? Optional.empty() : Optional.of("(stub transcript)");
        }

        @Override
        public List<TranscriptSegment> segments() {
            return segments;
        }
    }
}
