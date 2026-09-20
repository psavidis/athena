package com.athena.reviewrecorder;

import java.util.Objects;
import java.util.Optional;

/**
 * A {@link TranscriptSegment} paired with the entity/change reference
 * {@link TranscriptAligner} determined was in focus when it was spoken,
 * and its speaker if known (ticket #209). {@link #entityReference()} is
 * empty when the segment was spoken before any {@link SemanticEvent} had
 * been captured — left unaligned rather than guessed, per the ticket's
 * "perfect alignment is not required" scope.
 *
 * <p>This is still raw transcript data tied to an entity, not an
 * interpretation: it must never be presented as an explicit decision or
 * other {@link Moment} — that distinction is preserved by construction,
 * since this class has no relationship to {@link Moment} at all. A
 * developer who wants a moment recorded still tags one explicitly
 * (ticket #205); this class only helps them find the relevant stretch of
 * conversation to read while deciding whether to.
 */
public final class AlignedTranscriptSegment {

    private final TranscriptSegment segment;
    private final String entityReference;

    private AlignedTranscriptSegment(TranscriptSegment segment, String entityReference) {
        this.segment = segment;
        this.entityReference = entityReference;
    }

    static AlignedTranscriptSegment of(TranscriptSegment segment, String entityReference) {
        return new AlignedTranscriptSegment(Objects.requireNonNull(segment, "segment"), entityReference);
    }

    public TranscriptSegment segment() {
        return segment;
    }

    /** The entity/change reference in focus when this segment was spoken, or empty if none was. */
    public Optional<String> entityReference() {
        return Optional.ofNullable(entityReference);
    }
}
