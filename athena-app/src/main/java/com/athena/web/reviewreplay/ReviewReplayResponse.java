package com.athena.web.reviewreplay;

import com.athena.reviewreplay.ReviewReplay;

import java.util.List;

/** A read-only view of a {@link ReviewReplay} opened from a persisted artifact (ticket #210). */
public record ReviewReplayResponse(
        String recordingId,
        String repositoryFullName,
        int pullRequestNumber,
        String commitOrVersion,
        List<ResolvedReferenceResponse> resolvedReferences) {

    static ReviewReplayResponse of(ReviewReplay replay) {
        return new ReviewReplayResponse(
                replay.recordingId(),
                replay.repositoryFullName(),
                replay.pullRequestNumber(),
                replay.commitOrVersion(),
                replay.resolvedReferences().stream().map(ResolvedReferenceResponse::of).toList());
    }
}
