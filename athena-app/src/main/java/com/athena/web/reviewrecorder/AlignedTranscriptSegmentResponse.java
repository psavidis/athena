package com.athena.web.reviewrecorder;

import com.athena.reviewrecorder.AlignedTranscriptSegment;

import java.time.Instant;

/** A read-only view of one {@link AlignedTranscriptSegment} (ticket #209). */
public record AlignedTranscriptSegmentResponse(String text, Instant spokenAt, String speaker, String entityReference) {

    static AlignedTranscriptSegmentResponse of(AlignedTranscriptSegment aligned) {
        return new AlignedTranscriptSegmentResponse(
                aligned.segment().text(),
                aligned.segment().spokenAt(),
                aligned.segment().speaker().orElse(null),
                aligned.entityReference().orElse(null));
    }
}
