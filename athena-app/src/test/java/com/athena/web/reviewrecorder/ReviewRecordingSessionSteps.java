package com.athena.web.reviewrecorder;

import com.athena.git.TempDirectories;
import com.athena.plugins.PluginRegistry;
import com.athena.repository.ImportedPullRequest;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewrecorder.ReviewRecordingRegistry;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.PrAnalyzer;
import com.athena.semantic.ReviewStateStore;
import com.athena.web.Diff;
import com.athena.web.WebSession;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for the Review Recording session lifecycle feature
 * (ticket #203). Detroit-school: real {@link WebSession},
 * {@link ReviewRecordingRegistry}, and {@link ReviewRecordingController}
 * — no mocks — matching {@code LiveCodeReviewSessionSteps}'s own
 * precedent for the closest analogous feature in this codebase.
 *
 * <p>The recording's clock is the one legitimate whitebox seam here (a
 * non-deterministic system clock, per CODE_STYLE.md &sect;F): a mutable
 * fixed {@link Clock} lets "some time passes" be expressed deterministically.
 */
public class ReviewRecordingSessionSteps {

    private final WebSession webSession =
            new WebSession(new PrAnalyzer(PluginRegistry.languagePlugins(), PluginRegistry.frameworkPlugins()));
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-17T10:00:00Z"));
    private final ReviewRecordingRegistry registry = new ReviewRecordingRegistry(clock);
    private final ReviewRecordingController controller = new ReviewRecordingController(webSession, registry);

    private String recordingId;
    private ReviewRecordingSnapshot lastSnapshot;
    private ResponseStatusException failure;
    private boolean disclosureShown;

    private Path baseRoot;
    private Path headRoot;

    @Before
    public void createTempRoots() throws IOException {
        baseRoot = Files.createTempDirectory("athena-review-recording-base-");
        headRoot = Files.createTempDirectory("athena-review-recording-head-");
    }

    @After
    public void cleanUpTempRoots() {
        TempDirectories.deleteRecursively(baseRoot);
        TempDirectories.deleteRecursively(headRoot);
    }

    @Given("a developer has PR {int} in {string} selected")
    public void a_developer_has_pr_selected(int number, String repositoryFullName) {
        selectPullRequest(number, repositoryFullName);
    }

    @Given("a developer has no PR or Diff selected")
    public void a_developer_has_no_pr_or_diff_selected() {
        // no-op: a fresh WebSession starts with nothing selected
    }

    @Given("a developer has a standalone Diff selected, with no GitHub PR")
    public void a_developer_has_a_standalone_diff_selected() {
        webSession.selectDiff(new Diff(headRoot, baseRoot, headRoot, new ReviewStateStore(), new AnnotationBoard(),
                new ReviewSubmission()));
    }

    @When("the developer opens the Start Review Recording action")
    public void the_developer_opens_the_start_review_recording_action() {
        disclosureShown = true;
    }

    @Then("the developer is shown a disclosure of what will be captured")
    public void the_developer_is_shown_a_disclosure() {
        assertThat(disclosureShown).isTrue();
        assertThat(controller.captureDisclosure()).isNotBlank();
    }

    @When("the developer starts a Review Recording as {string}, having acknowledged the disclosure")
    public void the_developer_starts_a_review_recording_as(String displayName) {
        attemptStart(displayName, true);
    }

    @When("the developer attempts to start a Review Recording as {string}, without acknowledging the disclosure")
    public void the_developer_attempts_to_start_without_acknowledging(String displayName) {
        attemptStart(displayName, false);
    }

    @When("the developer attempts to start a Review Recording as {string}, having acknowledged the disclosure")
    public void the_developer_attempts_to_start_having_acknowledged(String displayName) {
        attemptStart(displayName, true);
    }

    private void attemptStart(String displayName, boolean disclosureAcknowledged) {
        try {
            ReviewRecordingStartResponse response =
                    controller.start(new StartReviewRecordingRequest(displayName, disclosureAcknowledged));
            recordingId = response.recordingId();
            lastSnapshot = response.snapshot();
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @Given("{string} has started a Review Recording for PR {int} in {string}")
    public void has_started_a_review_recording(String displayName, int number, String repositoryFullName) {
        selectPullRequest(number, repositoryFullName);
        attemptStart(displayName, true);
    }

    @Then("the recording exists")
    public void the_recording_exists() {
        assertThat(recordingId).isNotNull();
        assertThat(registry.find(recordingId)).isPresent();
    }

    @Then("the recording is active")
    public void the_recording_is_active() {
        assertThat(currentSnapshot().active()).isTrue();
    }

    @Then("the recording is no longer active")
    public void the_recording_is_no_longer_active() {
        assertThat(currentSnapshot().active()).isFalse();
    }

    @Then("{string} appears as a participant in the recording")
    @Then("{string} still appears as a participant in the recording")
    public void appears_as_a_participant(String displayName) {
        assertThat(currentSnapshot().participantDisplayNames()).contains(displayName);
    }

    @Then("the recording's participant count is {int}")
    public void the_recordings_participant_count_is(int count) {
        assertThat(currentSnapshot().participantCount()).isEqualTo(count);
    }

    @When("{string} joins that recording")
    public void joins_that_recording(String displayName) {
        try {
            lastSnapshot = controller.join(recordingId, new JoinReviewRecordingRequest(displayName));
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @When("some time passes during the recording")
    public void some_time_passes_during_the_recording() {
        clock.advance(Duration.ofMinutes(5));
    }

    @Then("the recording's elapsed time increases")
    public void the_recordings_elapsed_time_increases() {
        assertThat(currentSnapshot().elapsedSeconds()).isGreaterThan(0);
    }

    @When("{string} stops the recording")
    public void stops_the_recording(String displayName) {
        try {
            lastSnapshot = controller.stop(recordingId);
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @Given("{string} has stopped the recording")
    public void has_stopped_the_recording(String displayName) {
        stops_the_recording(displayName);
    }

    @When("{string} attempts to stop the recording again")
    public void attempts_to_stop_the_recording_again(String displayName) {
        stops_the_recording(displayName);
    }

    @When("a developer attempts to stop recording {string}")
    public void a_developer_attempts_to_stop_recording(String unknownRecordingId) {
        try {
            controller.stop(unknownRecordingId);
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @Then("the review recording request is rejected as invalid")
    public void the_review_recording_request_is_rejected_as_invalid() {
        assertThat(failure).isNotNull();
        assertThat(failure.getStatusCode().is4xxClientError()).isTrue();
    }

    private ReviewRecordingSnapshot currentSnapshot() {
        return controller.snapshot(recordingId);
    }

    private void selectPullRequest(int number, String repositoryFullName) {
        ImportedPullRequest pr = new ImportedPullRequest(number, "A PR", "author", "base-sha", "head-sha",
                List.of(), List.of());
        webSession.connect("test-token");
        webSession.select(new WebSession.SelectedPullRequest(
                pr, repositoryFullName, headRoot, baseRoot, headRoot, new ReviewStateStore(), new AnnotationBoard(),
                new ReviewSubmission()));
    }

    /** A {@link Clock} whose {@link #advance} lets "some time passes" steps move time forward deterministically. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }
}
