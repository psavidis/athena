package com.athena.web.reviewrecorder;

import com.athena.reviewrecorder.ReviewRecording;

import java.util.List;

/**
 * A read-only view of a {@link ReviewRecording} at one point in time
 * (ticket #203) — what {@link ReviewRecordingController} returns from
 * every action, including the elapsed time and participant count the
 * frontend's persistent recording-state indicator displays.
 */
public record ReviewRecordingSnapshot(
        String recordingId,
        String repositoryFullName,
        int pullRequestNumber,
        boolean active,
        long elapsedSeconds,
        int participantCount,
        List<String> participantDisplayNames) {

    static ReviewRecordingSnapshot of(ReviewRecording recording) {
        return new ReviewRecordingSnapshot(
                recording.id(),
                recording.repositoryFullName(),
                recording.pullRequestNumber(),
                recording.active(),
                recording.elapsed().getSeconds(),
                recording.participantCount(),
                List.copyOf(recording.participantDisplayNames()));
    }
}
