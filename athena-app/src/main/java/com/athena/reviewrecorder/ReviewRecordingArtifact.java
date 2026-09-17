package com.athena.reviewrecorder;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * A persisted snapshot of a stopped {@link ReviewRecording} (ticket
 * #207): its identity (repository/PR/commit), full timeline (events and
 * moments), and summary. This is the artifact contract Review Replay
 * (#164) reads — deliberately independent of {@link ReviewRecording}
 * itself (which stays in-memory-only and disappears once the
 * application restarts), so a persisted artifact keeps working even if
 * the in-memory aggregate's own shape changes later.
 */
public final class ReviewRecordingArtifact {

    private final String recordingId;
    private final String repositoryFullName;
    private final int pullRequestNumber;
    private final String commitOrVersion;
    private final boolean audioEnabled;
    private final List<SemanticEvent> events;
    private final List<Moment> moments;
    private final ReviewRecordingSummary summary;

    private ReviewRecordingArtifact(String recordingId, String repositoryFullName, int pullRequestNumber,
                                     String commitOrVersion, boolean audioEnabled, List<SemanticEvent> events,
                                     List<Moment> moments, ReviewRecordingSummary summary) {
        this.recordingId = recordingId;
        this.repositoryFullName = repositoryFullName;
        this.pullRequestNumber = pullRequestNumber;
        this.commitOrVersion = commitOrVersion;
        this.audioEnabled = audioEnabled;
        this.events = events;
        this.moments = moments;
        this.summary = summary;
    }

    /** Captures {@code recording}'s current state as a persistable artifact. */
    public static ReviewRecordingArtifact of(ReviewRecording recording) {
        Objects.requireNonNull(recording, "recording");
        return new ReviewRecordingArtifact(recording.id(), recording.repositoryFullName(),
                recording.pullRequestNumber(), recording.commitOrVersion(), recording.audioEnabled(),
                recording.events(), recording.moments(), recording.summary());
    }

    static ReviewRecordingArtifact of(String recordingId, String repositoryFullName, int pullRequestNumber,
                                       String commitOrVersion, boolean audioEnabled, List<SemanticEvent> events,
                                       List<Moment> moments, Duration duration) {
        return new ReviewRecordingArtifact(recordingId, repositoryFullName, pullRequestNumber, commitOrVersion,
                audioEnabled, List.copyOf(events), List.copyOf(moments), ReviewRecordingSummary.of(duration, moments));
    }

    public String recordingId() {
        return recordingId;
    }

    public String repositoryFullName() {
        return repositoryFullName;
    }

    public int pullRequestNumber() {
        return pullRequestNumber;
    }

    public String commitOrVersion() {
        return commitOrVersion;
    }

    /** Whether the developer opted in to audio capture when this recording was started (ticket #208). */
    public boolean audioEnabled() {
        return audioEnabled;
    }

    public List<SemanticEvent> events() {
        return events;
    }

    public List<Moment> moments() {
        return moments;
    }

    public ReviewRecordingSummary summary() {
        return summary;
    }
}
