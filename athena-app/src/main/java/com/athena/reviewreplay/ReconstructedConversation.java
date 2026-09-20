package com.athena.reviewreplay;

import com.athena.reviewrecorder.AlignedTranscriptSegment;

import java.util.List;
import java.util.Objects;

/**
 * Every aligned transcript segment about one entity, regrouped into a
 * single conversation a developer can read start-to-end (ticket #214).
 * Purely a verbatim regrouping of {@link AlignedTranscriptSegment}s
 * {@code com.athena.reviewrecorder.TranscriptAligner} (ticket #209)
 * already produced, in the order they were spoken — never a summary or
 * an inference. No question-to-answer or decision-rationale
 * interpretation is attempted: that would need real NLP/AI
 * infrastructure this ticket doesn't have (see {@link
 * ConversationReconstructor}'s own javadoc), so every segment here is
 * verbatim transcript by construction, not something that needs a
 * per-segment "is this real" label the way an AI-generated summary
 * would.
 */
public final class ReconstructedConversation {

    private final String entityReference;
    private final List<AlignedTranscriptSegment> segments;

    private ReconstructedConversation(String entityReference, List<AlignedTranscriptSegment> segments) {
        this.entityReference = entityReference;
        this.segments = segments;
    }

    static ReconstructedConversation of(String entityReference, List<AlignedTranscriptSegment> segments) {
        Objects.requireNonNull(entityReference, "entityReference");
        Objects.requireNonNull(segments, "segments");
        return new ReconstructedConversation(entityReference, List.copyOf(segments));
    }

    public String entityReference() {
        return entityReference;
    }

    /** Every segment about {@link #entityReference()}, in the order they were spoken — always verbatim transcript. */
    public List<AlignedTranscriptSegment> segments() {
        return segments;
    }
}
