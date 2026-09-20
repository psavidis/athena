package com.athena.reviewrecorder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
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
    private final boolean audioEnabled;
    private final Set<String> participantDisplayNames = new LinkedHashSet<>();
    private final List<SemanticEvent> events = new ArrayList<>();
    private final List<Moment> moments = new ArrayList<>();
    private final TranscriptionProvider transcriptionProvider;

    private Instant stoppedAt;

    private ReviewRecording(String repositoryFullName, int pullRequestNumber, String commitOrVersion,
                             String starterDisplayName, Clock clock, boolean audioEnabled,
                             TranscriptionProvider transcriptionProvider) {
        this.id = UUID.randomUUID().toString();
        this.repositoryFullName = repositoryFullName;
        this.pullRequestNumber = pullRequestNumber;
        this.commitOrVersion = commitOrVersion;
        this.clock = clock;
        this.startedAt = clock.instant();
        this.audioEnabled = audioEnabled;
        this.participantDisplayNames.add(starterDisplayName);
        this.transcriptionProvider = transcriptionProvider;
    }

    /** Starts a new recording about {@code repositoryFullName}#{@code pullRequestNumber} at {@code commitOrVersion}. */
    static ReviewRecording start(String repositoryFullName, int pullRequestNumber, String commitOrVersion,
                                  String starterDisplayName, Clock clock) {
        return start(repositoryFullName, pullRequestNumber, commitOrVersion, starterDisplayName, clock, false);
    }

    /**
     * Starts a new recording, recording whether the developer opted in to audio capture (ticket
     * #208) as an intent only — no real transcription provider exists yet (see {@link
     * TranscriptionProvider}), so enabling this never causes any audio to actually be captured.
     */
    static ReviewRecording start(String repositoryFullName, int pullRequestNumber, String commitOrVersion,
                                  String starterDisplayName, Clock clock, boolean audioEnabled) {
        return start(repositoryFullName, pullRequestNumber, commitOrVersion, starterDisplayName, clock, audioEnabled,
                new NoOpTranscriptionProvider());
    }

    /**
     * Starts a new recording with an explicit {@link TranscriptionProvider} (ticket #209) —
     * overridable so a test can supply one that actually produces segments, since {@link
     * NoOpTranscriptionProvider} (the default above) never does.
     */
    static ReviewRecording start(String repositoryFullName, int pullRequestNumber, String commitOrVersion,
                                  String starterDisplayName, Clock clock, boolean audioEnabled,
                                  TranscriptionProvider transcriptionProvider) {
        Objects.requireNonNull(repositoryFullName, "repositoryFullName");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(transcriptionProvider, "transcriptionProvider");
        if (repositoryFullName.isBlank()) {
            throw new IllegalArgumentException("repositoryFullName must not be blank");
        }
        String starter = requireDisplayName(starterDisplayName);
        return new ReviewRecording(repositoryFullName, pullRequestNumber, commitOrVersion, starter, clock,
                audioEnabled, transcriptionProvider);
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

    /** Whether the developer opted in to audio capture when starting this recording (ticket #208). */
    public boolean audioEnabled() {
        return audioEnabled;
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

    /** Appends {@code event} to this recording's event stream (ticket #204). Requires the recording still be active. */
    public synchronized void capture(SemanticEvent event) {
        requireActive();
        events.add(Objects.requireNonNull(event, "event"));
    }

    /** Every semantic event captured so far, in the order they were captured. */
    public synchronized List<SemanticEvent> events() {
        return List.copyOf(events);
    }

    /**
     * Tags the current moment as {@code kind} (ticket #205), referencing whichever
     * entity/change the most recently captured {@link SemanticEvent} concerns — or no
     * reference at all if nothing has been captured yet. Starts {@link MomentStatus#PENDING}
     * (ticket #206) — it isn't durable until {@link #confirmMoment} confirms it. Requires the
     * recording still be active.
     */
    public synchronized Moment tagMoment(MomentKind kind) {
        requireActive();
        String reference = events.isEmpty() ? null : events.get(events.size() - 1).reference();
        Moment moment = Moment.tagged(kind, reference, clock.instant());
        moments.add(moment);
        return moment;
    }

    /** Confirms the pending moment {@code momentId}, making it durable. Requires it exist and still be pending. */
    public synchronized void confirmMoment(String momentId) {
        replaceMoment(momentId, Moment::confirmed);
    }

    /** Rejects the pending moment {@code momentId}, discarding it. Requires it exist and still be pending. */
    public synchronized void rejectMoment(String momentId) {
        replaceMoment(momentId, Moment::rejected);
    }

    /** Changes the pending moment {@code momentId}'s kind to {@code newKind}. Requires it exist and still be pending. */
    public synchronized void editMoment(String momentId, MomentKind newKind) {
        replaceMoment(momentId, moment -> moment.withKind(newKind));
    }

    private void replaceMoment(String momentId, java.util.function.UnaryOperator<Moment> transition) {
        int index = indexOfMoment(momentId);
        moments.set(index, transition.apply(moments.get(index)));
    }

    private int indexOfMoment(String momentId) {
        for (int i = 0; i < moments.size(); i++) {
            if (moments.get(i).id().equals(momentId)) {
                return i;
            }
        }
        throw new NoSuchElementException("No moment " + momentId + " in this recording");
    }

    /** Every moment tagged so far, in the order they were tagged. */
    public synchronized List<Moment> moments() {
        return List.copyOf(moments);
    }

    /** This recording's summary (ticket #206): duration and confirmed-moment counts by kind. */
    public synchronized ReviewRecordingSummary summary() {
        return ReviewRecordingSummary.of(elapsed(), moments);
    }

    /**
     * This recording's transcript segments aligned to whichever entity was in focus when each
     * was spoken (ticket #209), via {@link TranscriptAligner}. Always empty today: {@link
     * #transcriptionProvider}'s only implementation, {@link NoOpTranscriptionProvider}, never
     * produces segments — see its javadoc for why that's the accepted, best-effort state rather
     * than a bug.
     */
    public synchronized List<AlignedTranscriptSegment> alignedTranscript() {
        return TranscriptAligner.align(transcriptionProvider.segments(), events);
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
