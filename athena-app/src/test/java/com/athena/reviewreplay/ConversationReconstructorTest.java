package com.athena.reviewreplay;

import com.athena.reviewrecorder.AlignedTranscriptSegment;
import com.athena.reviewrecorder.SemanticEvent;
import com.athena.reviewrecorder.SemanticEventType;
import com.athena.reviewrecorder.TranscriptAligner;
import com.athena.reviewrecorder.TranscriptSegment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dedicated unit test for {@link ConversationReconstructor} (ticket #214). */
class ConversationReconstructorTest {

    private static final Instant T0 = Instant.parse("2026-09-17T10:30:00Z");
    private static final Instant T1 = Instant.parse("2026-09-17T10:34:00Z");
    private static final Instant T2 = Instant.parse("2026-09-17T10:41:00Z");
    private static final Instant T3 = Instant.parse("2026-09-17T10:45:00Z");

    @Test
    void groupsAlignedSegmentsAboutTheSameEntityInSpeakingOrder() {
        SemanticEvent inspected = SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:PaymentProcessor", T1);
        TranscriptSegment first = TranscriptSegment.of("Could this execute twice?", T2, "Alice");
        TranscriptSegment second = TranscriptSegment.of("Only if the network retries.", T3, "Bob");
        List<AlignedTranscriptSegment> aligned = TranscriptAligner.align(List.of(first, second), List.of(inspected));

        ReconstructedConversation conversation =
                ConversationReconstructor.reconstruct(aligned, "entity:PaymentProcessor");

        assertThat(conversation.entityReference()).isEqualTo("entity:PaymentProcessor");
        assertThat(conversation.segments()).extracting(a -> a.segment().text())
                .containsExactly("Could this execute twice?", "Only if the network retries.");
    }

    @Test
    void excludesSegmentsAboutADifferentEntity() {
        SemanticEvent paymentProcessor = SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:PaymentProcessor", T1);
        SemanticEvent retryWorker = SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:RetryWorker", T3.minusSeconds(1));
        TranscriptSegment aboutPaymentProcessor = TranscriptSegment.of("Could this execute twice?", T2, "Alice");
        TranscriptSegment aboutRetryWorker = TranscriptSegment.of("Should this retry at all?", T3, "Bob");
        List<AlignedTranscriptSegment> aligned = TranscriptAligner.align(
                List.of(aboutPaymentProcessor, aboutRetryWorker), List.of(paymentProcessor, retryWorker));

        ReconstructedConversation conversation =
                ConversationReconstructor.reconstruct(aligned, "entity:PaymentProcessor");

        assertThat(conversation.segments()).hasSize(1);
        assertThat(conversation.segments().get(0).segment().text()).isEqualTo("Could this execute twice?");
    }

    @Test
    void excludesUnalignedSegments() {
        SemanticEvent inspected = SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:PaymentProcessor", T1);
        TranscriptSegment unaligned = TranscriptSegment.of("Let's get started", T0, "Bob");
        TranscriptSegment aligned1 = TranscriptSegment.of("Could this execute twice?", T2, "Alice");
        List<AlignedTranscriptSegment> aligned =
                TranscriptAligner.align(List.of(unaligned, aligned1), List.of(inspected));

        ReconstructedConversation conversation =
                ConversationReconstructor.reconstruct(aligned, "entity:PaymentProcessor");

        assertThat(conversation.segments()).hasSize(1);
        assertThat(conversation.segments().get(0).segment().text()).isEqualTo("Could this execute twice?");
    }

    @Test
    void anEmptyAlignedSegmentListReconstructsAnEmptyConversation() {
        ReconstructedConversation conversation = ConversationReconstructor.reconstruct(List.of(), "entity:PaymentProcessor");

        assertThat(conversation.segments()).isEmpty();
    }

    @Test
    void reconstructingForAnEntityWithNoDiscussionYieldsAnEmptyConversation() {
        SemanticEvent inspected = SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:PaymentProcessor", T1);
        TranscriptSegment segment = TranscriptSegment.of("Could this execute twice?", T2, "Alice");
        List<AlignedTranscriptSegment> aligned = TranscriptAligner.align(List.of(segment), List.of(inspected));

        ReconstructedConversation conversation = ConversationReconstructor.reconstruct(aligned, "entity:RetryWorker");

        assertThat(conversation.segments()).isEmpty();
    }

    @Test
    void aNullAlignedSegmentListIsRejected() {
        assertThatThrownBy(() -> ConversationReconstructor.reconstruct(null, "entity:PaymentProcessor"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aNullEntityReferenceIsRejected() {
        assertThatThrownBy(() -> ConversationReconstructor.reconstruct(List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
