package com.athena.reviewrecorder;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Every currently-tracked {@link ReviewRecording}, keyed by its id (ticket
 * #203). An application-scoped singleton, matching
 * {@code com.athena.livesession.LiveReviewSessionRegistry}'s own reasoning:
 * a recording started from one browser must be joinable/discoverable
 * across the whole application, not scoped to one browser's session.
 */
@Component
public class ReviewRecordingRegistry {

    private final ConcurrentMap<String, ReviewRecording> recordings = new ConcurrentHashMap<>();
    private final Clock clock;

    public ReviewRecordingRegistry(Clock clock) {
        this.clock = clock;
    }

    /** Starts and registers a new recording about {@code repositoryFullName}#{@code pullRequestNumber}. */
    public ReviewRecording start(String repositoryFullName, int pullRequestNumber, String commitOrVersion,
                                  String starterDisplayName) {
        return start(repositoryFullName, pullRequestNumber, commitOrVersion, starterDisplayName, false);
    }

    /** Starts and registers a new recording, with audio capture opted in or out (ticket #208). */
    public ReviewRecording start(String repositoryFullName, int pullRequestNumber, String commitOrVersion,
                                  String starterDisplayName, boolean audioEnabled) {
        ReviewRecording recording = ReviewRecording.start(
                repositoryFullName, pullRequestNumber, commitOrVersion, starterDisplayName, clock, audioEnabled);
        recordings.put(recording.id(), recording);
        return recording;
    }

    /**
     * Starts and registers a new recording with an explicit {@link TranscriptionProvider}
     * (ticket #209) — overridable so a test can supply one that actually produces transcript
     * segments to align, since the registry's own default ({@link NoOpTranscriptionProvider})
     * never does.
     */
    public ReviewRecording start(String repositoryFullName, int pullRequestNumber, String commitOrVersion,
                                  String starterDisplayName, boolean audioEnabled,
                                  TranscriptionProvider transcriptionProvider) {
        ReviewRecording recording = ReviewRecording.start(repositoryFullName, pullRequestNumber, commitOrVersion,
                starterDisplayName, clock, audioEnabled, transcriptionProvider);
        recordings.put(recording.id(), recording);
        return recording;
    }

    public Optional<ReviewRecording> find(String recordingId) {
        return Optional.ofNullable(recordings.get(recordingId));
    }

    /** Stops the recording {@code recordingId} names. Requires it exist and still be active. */
    public void stop(String recordingId) {
        find(recordingId).orElseThrow().stop();
    }
}
