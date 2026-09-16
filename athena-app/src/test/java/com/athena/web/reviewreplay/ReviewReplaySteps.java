package com.athena.web.reviewreplay;

import com.athena.reviewreplay.KnownEntityReferenceResolver;
import com.athena.reviewrecorder.Moment;
import com.athena.reviewrecorder.ReviewRecordingArtifact;
import com.athena.reviewrecorder.ReviewRecordingArtifactStore;
import com.athena.web.reviewrecorder.ReviewRecordingSessionSteps;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for loading and resolving a recorded review artifact
 * for Replay (ticket #210). The "started/tagged/confirmed/stopped a
 * recording" preconditions are already defined by
 * {@link ReviewRecordingSessionSteps} for the Review Recorder features;
 * re-declaring identical {@code @Given} text here would be a duplicate
 * step definition (Cucumber-JVM rejects that), so this class is
 * constructor-injected with that fixture instead — PicoContainer gives
 * both classes the same instance for a scenario — and calls its public
 * step methods directly, matching {@code KnowledgeFixtureSteps}'
 * established shared-steps-class convention. Shares the fixture's
 * {@code WebSession} so it sees the same selected PR/Diff the recording
 * was persisted against.
 *
 * <p>{@link ReviewReplayController}'s production resolver runs full PR
 * analysis against the currently-selected Diff, which the recording
 * fixture's empty temp git roots analyze to no Changes at all — not a
 * meaningful signal here. So this class substitutes a real, if minimal,
 * {@link KnownEntityReferenceResolver} backed by every entity name the
 * persisted artifact's own moments reference (an entity mentioned in the
 * recording is presumed still current, by default — matching a real
 * codebase where most entities *don't* get renamed between recording and
 * replay), at the same constructor seam {@code ReviewRecordingSessionSteps}'
 * own persistence-failure fixture already uses for {@code
 * artifactStoreFactory}. The one explicit override is the "renamed" step
 * below. Not a mock of an internal collaborator — the resolver still runs
 * for real; only its data source is test-controlled.
 */
public class ReviewReplaySteps {

    private final ReviewRecordingSessionSteps recordingSteps;
    private final Set<String> renamedAwayEntityNames = new LinkedHashSet<>();
    private final ReviewReplayController replayController;

    private ReviewReplayResponse replay;
    private ResponseStatusException failure;

    public ReviewReplaySteps(ReviewRecordingSessionSteps recordingSteps) {
        this.recordingSteps = recordingSteps;
        this.replayController = new ReviewReplayController(recordingSteps.webSession(),
                ReviewRecordingArtifactStore::new,
                diff -> new KnownEntityReferenceResolver(() -> currentlyKnownEntityNames(diff.headRoot())));
    }

    @Given("the {string} entity has since been renamed and no longer resolves")
    public void the_entity_has_since_been_renamed_and_no_longer_resolves(String entityName) {
        renamedAwayEntityNames.add(entityName);
    }

    private Set<String> currentlyKnownEntityNames(Path headRoot) {
        return new ReviewRecordingArtifactStore(headRoot).find(recordingSteps.recordingId())
                .map(ReviewRecordingArtifact::moments)
                .orElseGet(List::of)
                .stream()
                .map(Moment::reference)
                .filter(reference -> reference != null && reference.startsWith("entity:"))
                .map(reference -> reference.substring("entity:".length()))
                .filter(entityName -> !renamedAwayEntityNames.contains(entityName))
                .collect(Collectors.toSet());
    }

    @When("a developer opens a Replay of the persisted artifact")
    public void a_developer_opens_a_replay_of_the_persisted_artifact() {
        openReplay(recordingSteps.recordingId());
    }

    @When("a developer attempts to open a Replay of artifact {string}")
    public void a_developer_attempts_to_open_a_replay_of_artifact(String unknownRecordingId) {
        openReplay(unknownRecordingId);
    }

    private void openReplay(String id) {
        try {
            replay = replayController.open(id);
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @Then("the Replay reports repository {string}, PR {int}, and the recording's commit")
    public void the_replay_reports_repository_pr_and_the_recordings_commit(String repositoryFullName, int pullRequestNumber) {
        assertReplayIdentity(repositoryFullName, pullRequestNumber);
    }

    @Then("the Replay still reports repository {string}, PR {int}, and the recording's commit")
    public void the_replay_still_reports_repository_pr_and_the_recordings_commit(String repositoryFullName, int pullRequestNumber) {
        assertReplayIdentity(repositoryFullName, pullRequestNumber);
    }

    private void assertReplayIdentity(String repositoryFullName, int pullRequestNumber) {
        assertThat(replay).isNotNull();
        assertThat(replay.repositoryFullName()).isEqualTo(repositoryFullName);
        assertThat(replay.pullRequestNumber()).isEqualTo(pullRequestNumber);
        assertThat(replay.commitOrVersion()).isNotBlank();
    }

    @Then("the Replay's reference to the {string} entity is resolved")
    public void the_replays_reference_to_the_entity_is_resolved(String entityName) {
        assertThat(resolvedReferenceFor(entityName).resolved()).isTrue();
        assertThat(resolvedReferenceFor(entityName).resolvedLabel()).isEqualTo(entityName);
    }

    @Then("the Replay's reference to the {string} entity is shown as unresolved")
    public void the_replays_reference_to_the_entity_is_shown_as_unresolved(String entityName) {
        assertThat(resolvedReferenceFor(entityName).resolved()).isFalse();
        assertThat(resolvedReferenceFor(entityName).resolvedLabel()).isNull();
    }

    private ResolvedReferenceResponse resolvedReferenceFor(String entityName) {
        return replay.resolvedReferences().stream()
                .filter(r -> r.reference().equals("entity:" + entityName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No resolved reference for entity:" + entityName));
    }

    // "the review recording request is rejected as invalid" is already defined by
    // ReviewRecordingSessionSteps and reused as-is here — but that class asserts against its
    // own `failure` field, which this class's openReplay() never touches. So this class defines
    // its own failure assertion under a distinct step text instead of colliding with that one.
    @Then("the Replay request is rejected as invalid")
    public void the_replay_request_is_rejected_as_invalid() {
        assertThat(failure).isNotNull();
        assertThat(failure.getStatusCode().is4xxClientError()).isTrue();
    }
}
