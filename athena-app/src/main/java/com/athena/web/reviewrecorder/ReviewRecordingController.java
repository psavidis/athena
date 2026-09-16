package com.athena.web.reviewrecorder;

import com.athena.reviewrecorder.ReviewRecording;
import com.athena.reviewrecorder.ReviewRecordingRegistry;
import com.athena.reviewrecorder.SemanticEvent;
import com.athena.reviewrecorder.SemanticEventType;
import com.athena.web.WebSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;

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

    public ReviewRecordingController(WebSession session, ReviewRecordingRegistry registry, Clock clock) {
        this.session = session;
        this.registry = registry;
        this.clock = clock;
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
        ReviewRecording recording = registry.start(repositoryFullName, pullRequestNumber, commitOrVersion, displayName);
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
        return ReviewRecordingSnapshot.of(recording);
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
