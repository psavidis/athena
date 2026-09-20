package com.athena.reviewrecorder;

import java.util.List;
import java.util.Objects;

/**
 * Aligns a {@link ReviewRecording}'s transcript segments to whichever
 * entity/change was in focus at each segment's timestamp (ticket #209):
 * the most recently captured {@link SemanticEvent} at or before the
 * segment was spoken. Timestamp + entity-in-focus is the whole signal —
 * matching the ticket's own "Limitations of Scope": perfect alignment
 * against every spoken sentence is explicitly not required.
 *
 * <p>Speaker attribution is not aligner logic at all — a segment already
 * carries its own speaker (or lack of one) from the transcription
 * provider, so {@link #align} just relays it through unchanged onto each
 * {@link AlignedTranscriptSegment}.
 */
public final class TranscriptAligner {

    private TranscriptAligner() {
    }

    /**
     * Aligns {@code segments} against {@code events}, both assumed already in chronological
     * order (the order {@link ReviewRecording#events()} and a transcription provider's own
     * output naturally produce). A segment spoken before {@code events}' first entry aligns to
     * no entity at all, rather than guessing.
     */
    public static List<AlignedTranscriptSegment> align(List<TranscriptSegment> segments, List<SemanticEvent> events) {
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(events, "events");
        return segments.stream()
                .map(segment -> AlignedTranscriptSegment.of(segment, mostRecentReferenceAt(segment, events)))
                .toList();
    }

    private static String mostRecentReferenceAt(TranscriptSegment segment, List<SemanticEvent> events) {
        String reference = null;
        for (SemanticEvent event : events) {
            if (event.occurredAt().isAfter(segment.spokenAt())) {
                break;
            }
            reference = event.reference();
        }
        return reference;
    }
}
