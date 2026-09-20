package com.athena.reviewrecorder;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Dedicated unit test for {@link TranscriptAligner} (ticket #209). */
class TranscriptAlignerTest {

    private static final Instant T0 = Instant.parse("2026-09-17T10:30:00Z");
    private static final Instant T1 = Instant.parse("2026-09-17T10:34:00Z");
    private static final Instant T2 = Instant.parse("2026-09-17T10:41:00Z");
    private static final Instant T3 = Instant.parse("2026-09-17T10:50:00Z");

    @Test
    void alignsASegmentToTheEntityInFocusWhenItWasSpoken() {
        SemanticEvent inspected = SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:PaymentProcessor", T1);
        TranscriptSegment segment = TranscriptSegment.of("Could this execute twice?", T2, "Alice");

        List<AlignedTranscriptSegment> aligned = TranscriptAligner.align(List.of(segment), List.of(inspected));

        assertThat(aligned).hasSize(1);
        assertThat(aligned.get(0).entityReference()).contains("entity:PaymentProcessor");
    }

    @Test
    void alignsToTheMostRecentEntityInFocusNotALaterOne() {
        SemanticEvent first = SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:PaymentProcessor", T1);
        SemanticEvent later = SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:RetryWorker", T3);
        TranscriptSegment segment = TranscriptSegment.of("Could this execute twice?", T2, "Alice");

        List<AlignedTranscriptSegment> aligned = TranscriptAligner.align(List.of(segment), List.of(first, later));

        assertThat(aligned.get(0).entityReference()).contains("entity:PaymentProcessor");
    }

    @Test
    void aSegmentSpokenBeforeAnyEventIsLeftUnaligned() {
        SemanticEvent inspected = SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:PaymentProcessor", T1);
        TranscriptSegment segment = TranscriptSegment.of("Let's get started", T0, "Bob");

        List<AlignedTranscriptSegment> aligned = TranscriptAligner.align(List.of(segment), List.of(inspected));

        assertThat(aligned.get(0).entityReference()).isEmpty();
    }

    @Test
    void aSegmentSpokenAtTheExactSameInstantAsAnEventAlignsToThatEvent() {
        SemanticEvent inspected = SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:PaymentProcessor", T1);
        TranscriptSegment segment = TranscriptSegment.of("What is this?", T1, "Alice");

        List<AlignedTranscriptSegment> aligned = TranscriptAligner.align(List.of(segment), List.of(inspected));

        assertThat(aligned.get(0).entityReference()).contains("entity:PaymentProcessor");
    }

    @Test
    void anEmptyEventListLeavesEverySegmentUnaligned() {
        TranscriptSegment segment = TranscriptSegment.of("Could this execute twice?", T2, "Alice");

        List<AlignedTranscriptSegment> aligned = TranscriptAligner.align(List.of(segment), List.of());

        assertThat(aligned.get(0).entityReference()).isEmpty();
    }

    @Test
    void anEmptySegmentListProducesNoAlignedSegments() {
        SemanticEvent inspected = SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:PaymentProcessor", T1);

        List<AlignedTranscriptSegment> aligned = TranscriptAligner.align(List.of(), List.of(inspected));

        assertThat(aligned).isEmpty();
    }

    @Test
    void speakerAttributionIsCarriedThroughUnchanged() {
        SemanticEvent inspected = SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:PaymentProcessor", T1);
        TranscriptSegment withSpeaker = TranscriptSegment.of("Could this execute twice?", T2, "Alice");
        TranscriptSegment withoutSpeaker = TranscriptSegment.withoutSpeaker("Hmm.", T2);

        List<AlignedTranscriptSegment> aligned =
                TranscriptAligner.align(List.of(withSpeaker, withoutSpeaker), List.of(inspected));

        assertThat(aligned.get(0).segment().speaker()).contains("Alice");
        assertThat(aligned.get(1).segment().speaker()).isEmpty();
    }

    @Test
    void eachSegmentAlignsIndependently() {
        SemanticEvent first = SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:PaymentProcessor", T1);
        SemanticEvent second = SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:RetryWorker", T2);
        TranscriptSegment early = TranscriptSegment.of("First question", T1.plusSeconds(30), "Alice");
        TranscriptSegment late = TranscriptSegment.of("Second question", T3, "Bob");

        List<AlignedTranscriptSegment> aligned = TranscriptAligner.align(List.of(early, late), List.of(first, second));

        assertThat(aligned.get(0).entityReference()).contains("entity:PaymentProcessor");
        assertThat(aligned.get(1).entityReference()).contains("entity:RetryWorker");
    }
}
