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
 * Step definitions for the Review Recording session lifecycle (ticket
 * #203), semantic event capture (ticket #204), and explicit moment
 * tagging (ticket #205) features. Kept as one class since all three
 * share the same "Petros has started a Review Recording" fixture —
 * matching {@code LiveCodeReviewSessionSteps}'s own precedent of one
 * steps class per closely-related ticket group, for the closest
 * analogous feature in this codebase. Detroit-school: real
 * {@link WebSession}, {@link ReviewRecordingRegistry}, and
 * {@link ReviewRecordingController} — no mocks.
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
    private ReviewRecordingController controller = new ReviewRecordingController(webSession, registry, clock);

    private String recordingId;
    private String lastTaggedMomentId;
    private ReviewRecordingSummaryResponse stopSummary;
    private ReviewRecordingSnapshot lastSnapshot;
    private ResponseStatusException failure;
    private boolean disclosureShown;

    private Path baseRoot;
    private Path headRoot;

    /** The most recently started recording's id — for steps classes sharing this fixture (e.g. Review Replay's). */
    public String recordingId() {
        return recordingId;
    }

    /** This fixture's {@link WebSession} — for steps classes that must see the same selected PR/Diff (e.g. Review Replay's). */
    public WebSession webSession() {
        return webSession;
    }

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

    // --- Semantic event capture (ticket #204) ---

    @When("{string} navigates the canvas to the {string} component")
    public void navigates_the_canvas_to_the_component(String displayName, String componentName) {
        captureEvent("CANVAS_NAVIGATION", "component:" + componentName);
    }

    @When("{string} attempts to navigate the canvas to the {string} component")
    public void attempts_to_navigate_the_canvas_to_the_component(String displayName, String componentName) {
        captureEvent("CANVAS_NAVIGATION", "component:" + componentName);
    }

    @When("{string} changes the semantic zoom to {string}")
    public void changes_the_semantic_zoom_to(String displayName, String zoomLevel) {
        captureEvent("SEMANTIC_ZOOM_CHANGE", zoomLevel);
    }

    @When("{string} inspects the {string} entity")
    public void inspects_the_entity(String displayName, String entityName) {
        captureEvent("ENTITY_INSPECTED", "entity:" + entityName);
    }

    @When("{string} views the diff area for {string}")
    public void views_the_diff_area_for(String displayName, String filePath) {
        captureEvent("DIFF_AREA_VIEWED", filePath);
    }

    @When("{string} creates the comment {string} on the {string} component")
    public void creates_the_comment_on_the_component(String displayName, String text, String componentName) {
        captureEvent("COMMENT_CREATED", "component:" + componentName);
    }

    private void captureEvent(String type, String reference) {
        try {
            controller.captureEvent(recordingId, new CaptureSemanticEventRequest(type, reference));
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @Then("the recording's event stream includes a navigation event for the {string} component")
    public void the_event_stream_includes_a_navigation_event_for_the_component(String componentName) {
        assertEventPresent("CANVAS_NAVIGATION", "component:" + componentName);
    }

    @Then("the recording's event stream includes a semantic zoom event to {string}")
    public void the_event_stream_includes_a_semantic_zoom_event_to(String zoomLevel) {
        assertEventPresent("SEMANTIC_ZOOM_CHANGE", zoomLevel);
    }

    @Then("the recording's event stream includes an entity-inspected event for the {string} entity")
    public void the_event_stream_includes_an_entity_inspected_event_for_the_entity(String entityName) {
        assertEventPresent("ENTITY_INSPECTED", "entity:" + entityName);
    }

    @Then("the recording's event stream includes a diff-area-viewed event for {string}")
    public void the_event_stream_includes_a_diff_area_viewed_event_for(String filePath) {
        assertEventPresent("DIFF_AREA_VIEWED", filePath);
    }

    @Then("the recording's event stream includes a comment-created event referencing the {string} component")
    public void the_event_stream_includes_a_comment_created_event_referencing_the_component(String componentName) {
        assertEventPresent("COMMENT_CREATED", "component:" + componentName);
    }

    private void assertEventPresent(String type, String reference) {
        List<SemanticEventResponse> events = controller.events(recordingId);
        assertThat(events).anySatisfy(event -> {
            assertThat(event.type()).isEqualTo(type);
            assertThat(event.reference()).isEqualTo(reference);
        });
    }

    @Then("the recording's event stream lists the navigation event before the entity-inspected event")
    public void the_event_stream_lists_the_navigation_event_before_the_entity_inspected_event() {
        List<SemanticEventResponse> events = controller.events(recordingId);
        assertThat(events).extracting(SemanticEventResponse::type)
                .containsExactly("CANVAS_NAVIGATION", "ENTITY_INSPECTED");
    }

    // --- Explicit moment tagging (ticket #205) ---

    @When("{string} tags the current moment as a question")
    public void tags_the_current_moment_as_a_question(String displayName) {
        tagMoment("QUESTION");
    }

    @When("{string} tags the current moment as a decision")
    public void tags_the_current_moment_as_a_decision(String displayName) {
        tagMoment("DECISION");
    }

    @When("{string} tags the current moment as an insight")
    public void tags_the_current_moment_as_an_insight(String displayName) {
        tagMoment("INSIGHT");
    }

    @When("{string} tags the current moment as a concern")
    public void tags_the_current_moment_as_a_concern(String displayName) {
        tagMoment("CONCERN");
    }

    @When("{string} tags the current moment as an action")
    public void tags_the_current_moment_as_an_action(String displayName) {
        tagMoment("ACTION");
    }

    @When("{string} tags the current moment as a verification")
    public void tags_the_current_moment_as_a_verification(String displayName) {
        tagMoment("VERIFICATION");
    }

    @When("{string} attempts to tag the current moment as a question")
    public void attempts_to_tag_the_current_moment_as_a_question(String displayName) {
        tagMoment("QUESTION");
    }

    @When("{string} attempts to tag the current moment with an unknown kind")
    public void attempts_to_tag_the_current_moment_with_an_unknown_kind(String displayName) {
        tagMoment("NOT_A_REAL_KIND");
    }

    private void tagMoment(String kind) {
        try {
            lastTaggedMomentId = controller.tagMoment(recordingId, new TagMomentRequest(kind)).momentId();
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @Then("the recording's tagged moments include a question referencing the {string} entity")
    public void the_tagged_moments_include_a_question_referencing_the_entity(String entityName) {
        assertMomentPresent("QUESTION", "entity:" + entityName);
    }

    @Then("the recording's tagged moments include a decision referencing the {string} component")
    public void the_tagged_moments_include_a_decision_referencing_the_component(String componentName) {
        assertMomentPresent("DECISION", "component:" + componentName);
    }

    private void assertMomentPresent(String kind, String reference) {
        List<MomentResponse> moments = controller.moments(recordingId);
        assertThat(moments).anySatisfy(moment -> {
            assertThat(moment.kind()).isEqualTo(kind);
            assertThat(moment.reference()).isEqualTo(reference);
        });
    }

    @Then("the recording's tagged moments include an insight, a concern, an action, and a verification")
    public void the_tagged_moments_include_an_insight_a_concern_an_action_and_a_verification() {
        List<MomentResponse> moments = controller.moments(recordingId);
        assertThat(moments).extracting(MomentResponse::kind)
                .containsExactlyInAnyOrder("INSIGHT", "CONCERN", "ACTION", "VERIFICATION");
    }

    @Then("the recording's tagged moments list the question before the decision")
    public void the_tagged_moments_list_the_question_before_the_decision() {
        List<MomentResponse> moments = controller.moments(recordingId);
        assertThat(moments).extracting(MomentResponse::kind).containsExactly("QUESTION", "DECISION");
    }

    // --- Review timeline and session summary (ticket #206) ---

    @Given("{string} has tagged the current moment as a question")
    public void has_tagged_the_current_moment_as_a_question(String displayName) {
        tagMoment("QUESTION");
    }

    @Given("{string} has tagged the current moment as a decision")
    public void has_tagged_the_current_moment_as_a_decision(String displayName) {
        tagMoment("DECISION");
    }

    @When("{string} confirms that moment")
    public void confirms_that_moment(String displayName) {
        try {
            controller.confirmMoment(recordingId, lastTaggedMomentId);
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @Given("{string} has confirmed that moment")
    public void has_confirmed_that_moment(String displayName) {
        confirms_that_moment(displayName);
    }

    @When("{string} attempts to confirm that moment again")
    public void attempts_to_confirm_that_moment_again(String displayName) {
        confirms_that_moment(displayName);
    }

    @When("{string} edits that moment to a concern")
    public void edits_that_moment_to_a_concern(String displayName) {
        try {
            controller.editMoment(recordingId, lastTaggedMomentId, new TagMomentRequest("CONCERN"));
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @When("{string} rejects that moment")
    public void rejects_that_moment(String displayName) {
        try {
            controller.rejectMoment(recordingId, lastTaggedMomentId);
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @Then("the recording's timeline shows a pending question referencing the {string} entity")
    public void the_timeline_shows_a_pending_question_referencing_the_entity(String entityName) {
        assertTimelineMoment("QUESTION", "PENDING", "entity:" + entityName);
    }

    @Then("the recording's timeline shows a confirmed question referencing the {string} entity")
    public void the_timeline_shows_a_confirmed_question_referencing_the_entity(String entityName) {
        assertTimelineMoment("QUESTION", "CONFIRMED", "entity:" + entityName);
    }

    @Then("the recording's timeline shows a confirmed concern referencing the {string} entity")
    public void the_timeline_shows_a_confirmed_concern_referencing_the_entity(String entityName) {
        assertTimelineMoment("CONCERN", "CONFIRMED", "entity:" + entityName);
    }

    private void assertTimelineMoment(String kind, String status, String reference) {
        List<MomentResponse> moments = controller.moments(recordingId);
        assertThat(moments).anySatisfy(moment -> {
            assertThat(moment.kind()).isEqualTo(kind);
            assertThat(moment.status()).isEqualTo(status);
            assertThat(moment.reference()).isEqualTo(reference);
        });
    }

    @Then("the recording's timeline does not show a question referencing the {string} entity")
    public void the_timeline_does_not_show_a_question_referencing_the_entity(String entityName) {
        List<MomentResponse> moments = controller.moments(recordingId);
        assertThat(moments).noneSatisfy(moment -> {
            assertThat(moment.kind()).isEqualTo("QUESTION");
            assertThat(moment.reference()).isEqualTo("entity:" + entityName);
            assertThat(moment.status()).isNotEqualTo("REJECTED");
        });
    }

    @Then("the stop summary shows {int} question and {int} decision")
    public void the_stop_summary_shows_question_and_decision(int questionCount, int decisionCount) {
        stopSummary = controller.summary(recordingId);
        assertThat(stopSummary.momentCountsByKind().getOrDefault("QUESTION", 0)).isEqualTo(questionCount);
        assertThat(stopSummary.momentCountsByKind().getOrDefault("DECISION", 0)).isEqualTo(decisionCount);
    }

    @Then("the stop summary shows the recording's duration")
    public void the_stop_summary_shows_the_recordings_duration() {
        assertThat(stopSummary.durationSeconds()).isGreaterThanOrEqualTo(0);
    }

    @Then("the stop summary shows {int} questions")
    public void the_stop_summary_shows_questions(int questionCount) {
        ReviewRecordingSummaryResponse summary = controller.summary(recordingId);
        assertThat(summary.momentCountsByKind().getOrDefault("QUESTION", 0)).isEqualTo(questionCount);
    }

    // --- Artifact persistence (ticket #207) ---

    private ReviewRecordingArtifactResponse reopenedArtifact;

    @Given("persisting artifacts is currently failing")
    public void persisting_artifacts_is_currently_failing() throws IOException {
        Path notADirectory = Files.createTempFile("athena-review-recording-broken-root-", "");
        controller = new ReviewRecordingController(webSession, registry, clock,
                projectRoot -> new com.athena.reviewrecorder.ReviewRecordingArtifactStore(notADirectory));
    }

    @Then("the recording's artifact is persisted")
    public void the_recordings_artifact_is_persisted() {
        reopenedArtifact = controller.artifact(recordingId);
        assertThat(reopenedArtifact).isNotNull();
    }

    @Then("the persisted artifact is associated with {string}, PR {int}, and the recording's commit")
    public void the_persisted_artifact_is_associated_with(String repositoryFullName, int pullRequestNumber) {
        assertThat(reopenedArtifact.repositoryFullName()).isEqualTo(repositoryFullName);
        assertThat(reopenedArtifact.pullRequestNumber()).isEqualTo(pullRequestNumber);
        assertThat(reopenedArtifact.commitOrVersion()).isNotBlank();
    }

    @When("a developer reopens the persisted artifact")
    public void a_developer_reopens_the_persisted_artifact() {
        reopenedArtifact = controller.artifact(recordingId);
    }

    @When("a developer attempts to reopen artifact {string}")
    public void a_developer_attempts_to_reopen_artifact(String unknownRecordingId) {
        try {
            controller.artifact(unknownRecordingId);
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @Then("the reopened artifact shows the confirmed question referencing the {string} entity")
    public void the_reopened_artifact_shows_the_confirmed_question_referencing_the_entity(String entityName) {
        assertThat(reopenedArtifact.moments()).anySatisfy(moment -> {
            assertThat(moment.kind()).isEqualTo("QUESTION");
            assertThat(moment.status()).isEqualTo("CONFIRMED");
            assertThat(moment.reference()).isEqualTo("entity:" + entityName);
        });
    }

    @Then("the reopened artifact shows the recording's summary")
    public void the_reopened_artifact_shows_the_recordings_summary() {
        assertThat(reopenedArtifact.summary()).isNotNull();
        assertThat(reopenedArtifact.summary().momentCountsByKind()).isNotEmpty();
    }

    @Then("the recording's summary can still be read")
    public void the_recordings_summary_can_still_be_read() {
        assertThat(controller.summary(recordingId)).isNotNull();
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
