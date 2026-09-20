package com.athena.web.reviewrecorder;

import com.athena.reviewrecorder.ReviewRecording;

import java.time.Instant;
import java.util.List;

/**
 * A read-only view of a {@link ReviewRecording} at one point in time
 * (ticket #203) — what {@link ReviewRecordingController} returns from
 * every action, including the elapsed time and participant count the
 * frontend's persistent recording-state indicator displays.
 *
 * <p>{@link #startedAt()} (ticket #252) doubles as the clock-anchoring
 * basis a remote/call-based recording's participants need: each
 * participant computes their own upload timestamps as an offset from
 * this shared instant, rather than trusting their own machine's wall
 * clock directly — see {@code RemoteTranscriptMerger}'s own javadoc for
 * why its input streams' timestamps must already be comparable.
 */
public record ReviewRecordingSnapshot(
        String recordingId,
        String repositoryFullName,
        int pullRequestNumber,
        boolean active,
        boolean audioEnabled,
        Instant startedAt,
        long elapsedSeconds,
        int participantCount,
        List<String> participantDisplayNames) {

    static ReviewRecordingSnapshot of(ReviewRecording recording) {
        return new ReviewRecordingSnapshot(
                recording.id(),
                recording.repositoryFullName(),
                recording.pullRequestNumber(),
                recording.active(),
                recording.audioEnabled(),
                recording.startedAt(),
                recording.elapsed().getSeconds(),
                recording.participantCount(),
                List.copyOf(recording.participantDisplayNames()));
    }
}
