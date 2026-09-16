package com.athena.reviewrecorder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A Review Recording session (ticket #203): the explicit on/off capture
 * shell every other Review Recorder ticket builds on. This ticket alone
 * only tracks lifecycle (started/stopped) and who's present — no audio,
 * transcription, or semantic event capture yet.
 *
 * <p>Deliberately mirrors {@code com.athena.livesession.LiveReviewSession}'s
 * shape (repository, pull request, participants, a per-mutation
 * {@code synchronized(this)} guard) since a Review Recording is the same
 * kind of cross-participant shared aggregate, held by
 * {@link ReviewRecordingRegistry}.
 */
public final class ReviewRecording {

    private final String id;
    private final String repositoryFullName;
    private final int pullRequestNumber;
    private final String commitOrVersion;
    private final Clock clock;
    private final Instant startedAt;
    private final Set<String> participantDisplayNames = new LinkedHashSet<>();

    private Instant stoppedAt;

    private ReviewRecording(String repositoryFullName, int pullRequestNumber, String commitOrVersion,
                             String starterDisplayName, Clock clock) {
        this.id = UUID.randomUUID().toString();
        this.repositoryFullName = repositoryFullName;
        this.pullRequestNumber = pullRequestNumber;
        this.commitOrVersion = commitOrVersion;
        this.clock = clock;
        this.startedAt = clock.instant();
        this.participantDisplayNames.add(starterDisplayName);
    }

    /** Starts a new recording about {@code repositoryFullName}#{@code pullRequestNumber} at {@code commitOrVersion}. */
    static ReviewRecording start(String repositoryFullName, int pullRequestNumber, String commitOrVersion,
                                  String starterDisplayName, Clock clock) {
        Objects.requireNonNull(repositoryFullName, "repositoryFullName");
        Objects.requireNonNull(clock, "clock");
        if (repositoryFullName.isBlank()) {
            throw new IllegalArgumentException("repositoryFullName must not be blank");
        }
        String starter = requireDisplayName(starterDisplayName);
        return new ReviewRecording(repositoryFullName, pullRequestNumber, commitOrVersion, starter, clock);
    }

    public String id() {
        return id;
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

    public Instant startedAt() {
        return startedAt;
    }

    public synchronized boolean active() {
        return stoppedAt == null;
    }

    /** Every participant present in this recording, in the order they joined. */
    public synchronized Set<String> participantDisplayNames() {
        return Set.copyOf(participantDisplayNames);
    }

    public synchronized int participantCount() {
        return participantDisplayNames.size();
    }

    /** How long this recording has been running: from {@link #startedAt()} to now (if active) or to when it stopped. */
    public synchronized Duration elapsed() {
        Instant end = stoppedAt != null ? stoppedAt : clock.instant();
        return Duration.between(startedAt, end);
    }

    /** Adds {@code displayName} as a participant present in this recording. Requires the recording still be active. */
    public synchronized void join(String displayName) {
        requireActive();
        participantDisplayNames.add(requireDisplayName(displayName));
    }

    /** Stops this recording. Requires it not already be stopped. */
    public synchronized void stop() {
        requireActive();
        this.stoppedAt = clock.instant();
    }

    private void requireActive() {
        if (!active()) {
            throw new IllegalStateException("Review Recording " + id + " is not active");
        }
    }

    private static String requireDisplayName(String displayName) {
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        return displayName;
    }
}
