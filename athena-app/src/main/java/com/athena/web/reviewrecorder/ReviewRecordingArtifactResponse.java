package com.athena.web.reviewrecorder;

import com.athena.reviewrecorder.ReviewRecordingArtifact;

import java.util.List;

/** A read-only view of a persisted {@link ReviewRecordingArtifact} (ticket #207), reopened by id. */
public record ReviewRecordingArtifactResponse(
        String recordingId,
        String repositoryFullName,
        int pullRequestNumber,
        String commitOrVersion,
        List<SemanticEventResponse> events,
        List<MomentResponse> moments,
        ReviewRecordingSummaryResponse summary) {

    static ReviewRecordingArtifactResponse of(ReviewRecordingArtifact artifact) {
        return new ReviewRecordingArtifactResponse(
                artifact.recordingId(),
                artifact.repositoryFullName(),
                artifact.pullRequestNumber(),
                artifact.commitOrVersion(),
                artifact.events().stream().map(SemanticEventResponse::of).toList(),
                artifact.moments().stream().map(MomentResponse::of).toList(),
                ReviewRecordingSummaryResponse.of(artifact.summary()));
    }
}
