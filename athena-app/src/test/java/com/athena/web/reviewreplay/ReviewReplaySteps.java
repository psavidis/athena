package com.athena.web.reviewreplay;

import com.athena.git.TempDirectories;
import com.athena.memory.MemoryEntry;
import com.athena.memory.ProjectMemoryStore;
import com.athena.repository.ImportedPullRequest;
import com.athena.reviewcontext.ReviewSubmission;
import com.athena.reviewreplay.KnownEntityReferenceResolver;
import com.athena.reviewrecorder.Moment;
import com.athena.reviewrecorder.ReviewRecordingArtifact;
import com.athena.reviewrecorder.ReviewRecordingArtifactStore;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.ReviewStateStore;
import com.athena.web.WebSession;
import com.athena.web.reviewrecorder.ReviewRecordingSessionSteps;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
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
    private String lastPromotedFact;

    // --- Importing a shared artifact (ticket #217) ---

    private Path exportedArtifactFile;
    private ReviewRecordingArtifact exportedArtifact;
    private Path importingProjectHeadRoot;
    private Path importingProjectBaseRoot;

    public ReviewReplaySteps(ReviewRecordingSessionSteps recordingSteps) {
        this.recordingSteps = recordingSteps;
        this.replayController = new ReviewReplayController(recordingSteps.webSession(),
                ReviewRecordingArtifactStore::new,
                diff -> new KnownEntityReferenceResolver(() -> currentlyKnownEntityNames(diff.headRoot())),
                diff -> entityName -> Optional.empty());
    }

    @After
    public void cleanUpImportingProject() {
        if (importingProjectHeadRoot != null) {
            TempDirectories.deleteRecursively(importingProjectHeadRoot);
        }
        if (importingProjectBaseRoot != null) {
            TempDirectories.deleteRecursively(importingProjectBaseRoot);
        }
        if (exportedArtifactFile != null) {
            TempDirectories.deleteRecursively(exportedArtifactFile.getParent());
        }
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

    // "opens the Replay's outcome" (ticket #215) reuses the same open() call — the outcome is
    // just one more field on the already-open ReviewReplayResponse, not a separate endpoint.
    @When("a developer opens the Replay's outcome")
    public void a_developer_opens_the_replays_outcome() {
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

    // --- Review outcome and derived learnings (ticket #215) ---

    @Then("the outcome's learned section has {int} item about the {string} entity")
    public void the_outcomes_learned_section_has_item_about_the_entity(int count, String entityName) {
        assertOutcomeSection(replay.outcome().learned(), count, entityName);
    }

    @Then("the outcome's decided section has {int} item about the {string} entity")
    public void the_outcomes_decided_section_has_item_about_the_entity(int count, String entityName) {
        assertOutcomeSection(replay.outcome().decided(), count, entityName);
    }

    @Then("the outcome's unresolved section has {int} item about the {string} entity")
    public void the_outcomes_unresolved_section_has_item_about_the_entity(int count, String entityName) {
        assertOutcomeSection(replay.outcome().unresolved(), count, entityName);
    }

    @Then("the outcome's actions section has {int} item about the {string} entity")
    public void the_outcomes_actions_section_has_item_about_the_entity(int count, String entityName) {
        assertOutcomeSection(replay.outcome().actions(), count, entityName);
    }

    private void assertOutcomeSection(List<OutcomeItemResponse> section, int count, String entityName) {
        assertThat(section).hasSize(count);
        assertThat(section).extracting(OutcomeItemResponse::reference).contains("entity:" + entityName);
    }

    @Then("the outcome's learned section is empty")
    public void the_outcomes_learned_section_is_empty() {
        assertThat(replay.outcome().learned()).isEmpty();
    }

    @Then("the outcome's decided section is empty")
    public void the_outcomes_decided_section_is_empty() {
        assertThat(replay.outcome().decided()).isEmpty();
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

    // --- Promoting a learning or decision to project memory (ticket #216) ---

    @When("a developer promotes that learned item to project memory as {string}")
    public void a_developer_promotes_that_learned_item_to_project_memory_as(String fact) {
        promoteTheOnlyItemIn("LEARNED", fact);
    }

    @When("a developer promotes that decided item to project memory as {string}")
    public void a_developer_promotes_that_decided_item_to_project_memory_as(String fact) {
        promoteTheOnlyItemIn("DECIDED", fact);
    }

    private void promoteTheOnlyItemIn(String section, String fact) {
        openReplay(recordingSteps.recordingId());
        String reference = ("LEARNED".equals(section) ? replay.outcome().learned() : replay.outcome().decided())
                .get(0).reference();
        promote(recordingSteps.recordingId(), section, reference, fact);
    }

    @When("a developer attempts to promote a learning to project memory as {string}")
    public void a_developer_attempts_to_promote_a_learning_to_project_memory_as(String fact) {
        promote("does-not-matter", "LEARNED", null, fact);
    }

    private void promote(String recordingId, String section, String reference, String fact) {
        lastPromotedFact = fact;
        try {
            replayController.promoteOutcomeItem(recordingId, new PromoteOutcomeItemRequest(section, reference, fact));
            failure = null;
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    @Then("the promotion request is rejected as invalid")
    public void the_promotion_request_is_rejected_as_invalid() {
        assertThat(failure).isNotNull();
        assertThat(failure.getStatusCode().is4xxClientError()).isTrue();
    }

    @Then("project memory has an entry {string}")
    public void project_memory_has_an_entry(String fact) {
        assertThat(entriesFor(fact)).isNotEmpty();
    }

    @Then("project memory has exactly {int} entry {string}")
    public void project_memory_has_exactly_entry(int count, String fact) {
        assertThat(entriesFor(fact)).hasSize(count);
    }

    @Then("that entry is developer-confirmed")
    public void that_entry_is_developer_confirmed() {
        assertThat(entriesFor(lastPromotedFact).get(0).developerConfirmed()).isTrue();
    }

    @Then("that entry's evidence mentions {string}, PR {int}, and {string}")
    public void that_entrys_evidence_mentions(String repositoryFullName, int pullRequestNumber, String entityName) {
        String evidence = entriesFor(lastPromotedFact).get(0).evidence();
        assertThat(evidence).contains(repositoryFullName).contains("PR " + pullRequestNumber).contains(entityName);
    }

    private List<MemoryEntry> entriesFor(String fact) {
        return new ProjectMemoryStore(recordingSteps.webSession().currentDiff().orElseThrow().headRoot())
                .entries().stream().filter(e -> e.fact().equals(fact)).toList();
    }

    @Given("the recording's artifact has been exported to a shared file")
    public void the_recordings_artifact_has_been_exported_to_a_shared_file() throws IOException {
        // "Exported" means copied out to an independent location: selecting a different project
        // below (selectImportingProject) replaces the current WebSession selection, which frees
        // the recording's own headRoot (see WebSession#select) — the shared file must survive
        // that, the same way a real teammate's exported file lives independently of the
        // recorder's own machine.
        Path recordingHeadRoot = recordingSteps.webSession().currentDiff().orElseThrow().headRoot();
        ReviewRecordingArtifact artifact = new ReviewRecordingArtifactStore(recordingHeadRoot)
                .find(recordingSteps.recordingId())
                .orElseThrow();
        Path sharedDir = Files.createTempDirectory("athena-review-replay-shared-artifact-");
        exportedArtifactFile = sharedDir.resolve(artifact.recordingId() + ".json");
        new ReviewRecordingArtifactStore(sharedDir).persist(artifact);
        Files.move(sharedDir.resolve(".athena").resolve("review-recordings").resolve(artifact.recordingId() + ".json"),
                exportedArtifactFile);
        exportedArtifact = artifact;
    }

    @Given("that same artifact is already stored locally")
    public void that_same_artifact_is_already_stored_locally() {
        selectImportingProject("acme/widgets");
        new ReviewRecordingArtifactStore(importingProjectHeadRoot).persist(exportedArtifact);
    }

    @When("a developer imports that shared file into a project selected on {string}")
    public void a_developer_imports_that_shared_file_into_a_project_selected_on(String repositoryFullName) {
        if (importingProjectHeadRoot == null) {
            selectImportingProject(repositoryFullName);
        }
        importSharedFile(exportedArtifactFile.toString());
    }

    @When("a developer imports a malformed shared file into a project selected on {string}")
    public void a_developer_imports_a_malformed_shared_file_into_a_project_selected_on(String repositoryFullName)
            throws IOException {
        selectImportingProject(repositoryFullName);
        Path malformed = Files.createTempFile("athena-review-replay-malformed-shared-artifact-", ".json");
        Files.writeString(malformed, "not valid json");
        importSharedFile(malformed.toString());
    }

    private void importSharedFile(String filePath) {
        try {
            replay = replayController.importSharedArtifact(new ImportSharedArtifactRequest(filePath));
        } catch (ResponseStatusException e) {
            failure = e;
        }
    }

    private void selectImportingProject(String repositoryFullName) {
        try {
            importingProjectBaseRoot = Files.createTempDirectory("athena-review-replay-import-base-");
            importingProjectHeadRoot = Files.createTempDirectory("athena-review-replay-import-head-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        WebSession webSession = recordingSteps.webSession();
        ImportedPullRequest pr = new ImportedPullRequest(99, "Importing PR", "author", "base-sha", "head-sha",
                List.of(), List.of());
        webSession.connect("test-token");
        webSession.select(new WebSession.SelectedPullRequest(
                pr, repositoryFullName, importingProjectHeadRoot, importingProjectBaseRoot, importingProjectHeadRoot,
                new ReviewStateStore(), new AnnotationBoard(), new ReviewSubmission()));
    }

    @Then("the import succeeds")
    public void the_import_succeeds() {
        assertThat(failure).isNull();
        assertThat(replay).isNotNull();
    }

    @Then("a developer can open the imported artifact as a Replay")
    public void a_developer_can_open_the_imported_artifact_as_a_replay() {
        openReplay(replay.recordingId());
        assertThat(failure).isNull();
        assertThat(replay).isNotNull();
    }

    @Then("the import is rejected as invalid")
    public void the_import_is_rejected_as_invalid() {
        assertThat(failure).isNotNull();
        assertThat(failure.getStatusCode().is4xxClientError()).isTrue();
    }
}
