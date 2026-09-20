package com.athena.reviewreplay;

import com.athena.reviewrecorder.AlignedTranscriptSegment;

import java.util.List;
import java.util.Objects;

/**
 * Reconstructs the conversation around one semantic entity from aligned
 * transcript segments (ticket #214) — a verbatim regrouping by entity
 * reference, in speaking order, of whatever {@code
 * com.athena.reviewrecorder.TranscriptAligner} (ticket #209) already
 * aligned. A segment #209 left unaligned (no entity reference) is never
 * included in any reconstructed conversation.
 *
 * <p>This ticket's own "Functional Requirements" ask for "question→answer
 * and decision-rationale summaries," but that is explicitly out of scope
 * here (confirmed with the user 2026-09-20): summarizing or inferring
 * structure from a conversation needs real NLP/AI infrastructure this
 * codebase doesn't have for this purpose — {@code com.athena.ai.AiProvider}
 * is scoped to post-review finding analysis (a different shape entirely),
 * and reusing or extending it for general summarization would be a scope
 * decision this ticket doesn't make. What ships here is the mechanically
 * derivable part: grouping and ordering, always labeled as verbatim
 * transcript (see {@link ReconstructedConversation}'s own javadoc) —
 * never presented as an interpretation, since none is attempted.
 */
public final class ConversationReconstructor {

    private ConversationReconstructor() {
    }

    /**
     * Every aligned segment referencing {@code entityReference}, in the order {@code
     * alignedSegments} already carries them (the order {@code TranscriptAligner#align} preserves
     * from its input). Segments about a different entity, or left unaligned, are excluded.
     */
    public static ReconstructedConversation reconstruct(List<AlignedTranscriptSegment> alignedSegments,
                                                          String entityReference) {
        Objects.requireNonNull(alignedSegments, "alignedSegments");
        Objects.requireNonNull(entityReference, "entityReference");
        List<AlignedTranscriptSegment> matching = alignedSegments.stream()
                .filter(segment -> segment.entityReference().filter(entityReference::equals).isPresent())
                .toList();
        return ReconstructedConversation.of(entityReference, matching);
    }
}
