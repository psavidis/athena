package com.athena.web.reviewrecorder;

import com.athena.reviewrecorder.Moment;
import com.athena.reviewrecorder.MomentKind;
import com.athena.reviewrecorder.ReviewRecording;
import com.athena.reviewrecorder.ReviewRecordingArtifact;
import com.athena.reviewrecorder.ReviewRecordingArtifactStore;
import com.athena.reviewrecorder.ReviewRecordingRegistry;
import com.athena.reviewrecorder.SemanticEvent;
import com.athena.reviewrecorder.SemanticEventType;
import com.athena.web.Diff;
import com.athena.web.WebSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * The web entry point for a Review Recording (ticket #203): the capture
 * disclosure text, starting a recording from the caller's current review,
 * joining an active one, reading its current snapshot, and stopping it.
 *
 * <p>Only {@link #start} consults {@link WebSession} (to read the
 * caller's currently-selected PR/Diff) — matching
 * {@code LiveReviewSessionController}'s own precedent for the same
 * reason: every other action is looked up purely by recording id.
 */
@RestController
@RequestMapping("/api/review-recordings")
public class ReviewRecordingController {

    private static final String CAPTURE_DISCLOSURE =
            "Starting a Review Recording captures: canvas navigation, the code/changes discussed, "
                    + "questions and decisions raised, and (only if you enable it) audio and a transcript. "
                    + "Nothing is captured until you start, and you can stop at any time.";

    private final WebSession session;
    private final ReviewRecordingRegistry registry;
    private final Clock clock;
    private final Function<Path, ReviewRecordingArtifactStore> artifactStoreFactory;

    @Autowired
    public ReviewRecordingController(WebSession session, ReviewRecordingRegistry registry, Clock clock) {
        this(session, registry, clock, ReviewRecordingArtifactStore::new);
    }

    /**
     * @param artifactStoreFactory resolves the {@link ReviewRecordingArtifactStore} for a given
     *                              project root — overridable so a test can substitute a store
     *                              that fails persistence deterministically (ticket #207)
     */
    ReviewRecordingController(WebSession session, ReviewRecordingRegistry registry, Clock clock,
                               Function<Path, ReviewRecordingArtifactStore> artifactStoreFactory) {
        this.session = session;
        this.registry = registry;
        this.clock = clock;
        this.artifactStoreFactory = artifactStoreFactory;
    }

    @GetMapping("/capture-disclosure")
    public String captureDisclosure() {
        return CAPTURE_DISCLOSURE;
    }

    @PostMapping
    public ReviewRecordingStartResponse start(@RequestBody StartReviewRecordingRequest request) {
        if (!request.disclosureAcknowledged()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The capture disclosure must be acknowledged before starting a Review Recording");
        }
        session.currentDiff().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                "Select a Pull Request or Diff before starting a Review Recording"));
        String displayName = requireDisplayName(request.displayName());
        // A PR-backed selection carries a real repository/PR number/commit for the recording to display;
        // a standalone Diff has neither, matching LiveReviewSessionController's own precedent for this split.
        String repositoryFullName = session.selectedPullRequest()
                .map(WebSession.SelectedPullRequest::repositoryFullName)
                .orElse("Local diff comparison");
        int pullRequestNumber = session.selectedPullRequest()
                .map(selected -> selected.pullRequest().number())
                .orElse(0);
        String commitOrVersion = session.selectedPullRequest()
                .map(selected -> selected.pullRequest().headRevision())
                .orElse("local");
        ReviewRecording recording = registry.start(
                repositoryFullName, pullRequestNumber, commitOrVersion, displayName, request.audioEnabled());
        return new ReviewRecordingStartResponse(recording.id(), ReviewRecordingSnapshot.of(recording));
    }

    @PostMapping("/{id}/join")
    public ReviewRecordingSnapshot join(@PathVariable String id, @RequestBody JoinReviewRecordingRequest request) {
        ReviewRecording recording = requireRecording(id);
        String displayName = requireDisplayName(request.displayName());
        try {
            recording.join(displayName);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        return ReviewRecordingSnapshot.of(recording);
    }

    @GetMapping("/{id}")
    public ReviewRecordingSnapshot snapshot(@PathVariable String id) {
        return ReviewRecordingSnapshot.of(requireRecording(id));
    }

    @PostMapping("/{id}/stop")
    public ReviewRecordingSnapshot stop(@PathVariable String id) {
        ReviewRecording recording = requireRecording(id);
        try {
            recording.stop();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        // A persistence failure must not corrupt the recording's own in-memory state (ticket #207):
        // this runs after stop() has already succeeded, and never calls back into `recording` on
        // failure, so the caller still gets a normal, intact snapshot either way.
        try {
            session.currentDiff().ifPresent(diff ->
                    artifactStoreFactory.apply(diff.headRoot()).persist(ReviewRecordingArtifact.of(recording)));
        } catch (UncheckedIOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to persist Review Recording artifact: " + e.getMessage());
        }
        return ReviewRecordingSnapshot.of(recording);
    }

    @GetMapping("/artifacts/{recordingId}")
    public ReviewRecordingArtifactResponse artifact(@PathVariable String recordingId) {
        Path projectRoot = session.currentDiff()
                .map(Diff::headRoot)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Select a Pull Request or Diff before reopening a Review Recording artifact"));
        return artifactStoreFactory.apply(projectRoot).find(recordingId)
                .map(ReviewRecordingArtifactResponse::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No such Review Recording artifact"));
    }

    @PostMapping("/{id}/events")
    public void captureEvent(@PathVariable String id, @RequestBody CaptureSemanticEventRequest request) {
        ReviewRecording recording = requireRecording(id);
        SemanticEventType type = requireEventType(request.type());
        try {
            recording.capture(SemanticEvent.of(type, request.reference(), clock.instant()));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{id}/events")
    public List<SemanticEventResponse> events(@PathVariable String id) {
        ReviewRecording recording = requireRecording(id);
        return recording.events().stream().map(SemanticEventResponse::of).toList();
    }

    private SemanticEventType requireEventType(String type) {
        try {
            return SemanticEventType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown semantic event type: " + type);
        }
    }

    @PostMapping("/{id}/moments")
    public MomentResponse tagMoment(@PathVariable String id, @RequestBody TagMomentRequest request) {
        ReviewRecording recording = requireRecording(id);
        MomentKind kind = requireMomentKind(request.kind());
        try {
            return MomentResponse.of(recording.tagMoment(kind));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @GetMapping("/{id}/moments")
    public List<MomentResponse> moments(@PathVariable String id) {
        ReviewRecording recording = requireRecording(id);
        return recording.moments().stream().map(MomentResponse::of).toList();
    }

    @PostMapping("/{id}/moments/{momentId}/confirm")
    public void confirmMoment(@PathVariable String id, @PathVariable String momentId) {
        ReviewRecording recording = requireRecording(id);
        try {
            recording.confirmMoment(momentId);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/{id}/moments/{momentId}/reject")
    public void rejectMoment(@PathVariable String id, @PathVariable String momentId) {
        ReviewRecording recording = requireRecording(id);
        try {
            recording.rejectMoment(momentId);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/{id}/moments/{momentId}/edit")
    public void editMoment(@PathVariable String id, @PathVariable String momentId, @RequestBody TagMomentRequest request) {
        ReviewRecording recording = requireRecording(id);
        MomentKind kind = requireMomentKind(request.kind());
        try {
            recording.editMoment(momentId, kind);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @GetMapping("/{id}/summary")
    public ReviewRecordingSummaryResponse summary(@PathVariable String id) {
        ReviewRecording recording = requireRecording(id);
        return ReviewRecordingSummaryResponse.of(recording.summary());
    }

    /**
     * This recording's transcript aligned to its semantic events (ticket #209). Always an empty
     * list today — see {@link com.athena.reviewrecorder.NoOpTranscriptionProvider}'s javadoc.
     */
    @GetMapping("/{id}/transcript/aligned")
    public List<AlignedTranscriptSegmentResponse> alignedTranscript(@PathVariable String id) {
        ReviewRecording recording = requireRecording(id);
        return recording.alignedTranscript().stream().map(AlignedTranscriptSegmentResponse::of).toList();
    }

    private MomentKind requireMomentKind(String kind) {
        try {
            return MomentKind.valueOf(kind);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown moment kind: " + kind);
        }
    }

    private String requireDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName must not be blank");
        }
        return displayName;
    }

    private ReviewRecording requireRecording(String id) {
        try {
            return registry.find(id).orElseThrow();
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such Review Recording");
        }
    }
}
