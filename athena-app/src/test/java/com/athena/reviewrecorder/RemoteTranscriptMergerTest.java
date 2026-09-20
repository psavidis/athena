package com.athena.reviewrecorder;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dedicated unit test for {@link RemoteTranscriptMerger} (ticket #251). */
class RemoteTranscriptMergerTest {

    private static final Instant T0 = Instant.parse("2026-09-17T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-09-17T10:00:05Z");
    private static final Instant T2 = Instant.parse("2026-09-17T10:00:10Z");

    @Test
    void mergesTwoParticipantsStreamsInChronologicalOrder() {
        TranscriptSegment aliceFirst = TranscriptSegment.of("Could this execute twice?", T0, "Alice");
        TranscriptSegment bobReply = TranscriptSegment.of("Only with a network retry.", T1, "Bob");
        TranscriptSegment aliceFollowUp = TranscriptSegment.of("Let's add an idempotency key.", T2, "Alice");

        List<TranscriptSegment> merged = RemoteTranscriptMerger.merge(
                List.of(List.of(aliceFirst, aliceFollowUp), List.of(bobReply)));

        assertThat(merged).extracting(TranscriptSegment::text)
                .containsExactly("Could this execute twice?", "Only with a network retry.", "Let's add an idempotency key.");
    }

    @Test
    void aMissingParticipantStreamContributesNothingButDoesNotFailTheMerge() {
        TranscriptSegment aliceOnly = TranscriptSegment.of("Anyone there?", T0, "Alice");

        List<TranscriptSegment> merged = RemoteTranscriptMerger.merge(List.of(List.of(aliceOnly), List.of()));

        assertThat(merged).containsExactly(aliceOnly);
    }

    @Test
    void everyStreamEmptyProducesAnEmptyMergeRatherThanFailing() {
        List<TranscriptSegment> merged = RemoteTranscriptMerger.merge(List.of(List.of(), List.of()));

        assertThat(merged).isEmpty();
    }

    @Test
    void noStreamsAtAllProducesAnEmptyMerge() {
        List<TranscriptSegment> merged = RemoteTranscriptMerger.merge(List.of());

        assertThat(merged).isEmpty();
    }

    @Test
    void segmentsAtTheExactSameInstantKeepTheirRelativeStreamOrder() {
        TranscriptSegment alice = TranscriptSegment.of("Talking over Bob", T0, "Alice");
        TranscriptSegment bob = TranscriptSegment.of("Talking over Alice", T0, "Bob");

        List<TranscriptSegment> merged = RemoteTranscriptMerger.merge(List.of(List.of(alice), List.of(bob)));

        assertThat(merged).extracting(TranscriptSegment::speaker)
                .containsExactly(java.util.Optional.of("Alice"), java.util.Optional.of("Bob"));
    }

    @Test
    void aNullStreamsListIsRejected() {
        assertThatThrownBy(() -> RemoteTranscriptMerger.merge(null))
                .isInstanceOf(NullPointerException.class);
    }
}
