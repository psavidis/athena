package com.athena.reviewreplay;

import com.athena.reviewrecorder.AlignedTranscriptSegment;
import com.athena.reviewrecorder.SemanticEvent;
import com.athena.reviewrecorder.SemanticEventType;
import com.athena.reviewrecorder.TranscriptAligner;
import com.athena.reviewrecorder.TranscriptSegment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dedicated unit test for {@link ReconstructedConversation} (ticket #214). */
class ReconstructedConversationTest {

    private static final Instant T1 = Instant.parse("2026-09-17T10:34:00Z");
    private static final Instant T2 = Instant.parse("2026-09-17T10:41:00Z");

    @Test
    void carriesItsEntityReferenceAndSegments() {
        SemanticEvent inspected = SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:PaymentProcessor", T1);
        TranscriptSegment segment = TranscriptSegment.of("Could this execute twice?", T2, "Alice");
        List<AlignedTranscriptSegment> aligned = TranscriptAligner.align(List.of(segment), List.of(inspected));

        ReconstructedConversation conversation = ReconstructedConversation.of("entity:PaymentProcessor", aligned);

        assertThat(conversation.entityReference()).isEqualTo("entity:PaymentProcessor");
        assertThat(conversation.segments()).isEqualTo(aligned);
    }

    @Test
    void segmentsAreDefensivelyCopied() {
        TranscriptSegment segment = TranscriptSegment.of("Late addition", T2, "Alice");
        List<AlignedTranscriptSegment> aligned = TranscriptAligner.align(List.of(segment), List.of());
        ArrayList<AlignedTranscriptSegment> mutable = new ArrayList<>(aligned);
        ReconstructedConversation conversation = ReconstructedConversation.of("entity:PaymentProcessor", mutable);

        mutable.clear();

        assertThat(conversation.segments()).hasSize(1);
    }

    @Test
    void aNullEntityReferenceIsRejected() {
        assertThatThrownBy(() -> ReconstructedConversation.of(null, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aNullSegmentListIsRejected() {
        assertThatThrownBy(() -> ReconstructedConversation.of("entity:PaymentProcessor", null))
                .isInstanceOf(NullPointerException.class);
    }
}
