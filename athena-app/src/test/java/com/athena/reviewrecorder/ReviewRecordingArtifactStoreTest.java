package com.athena.reviewrecorder;

import com.athena.git.TempDirectories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated unit test for {@link ReviewRecordingArtifactStore} (ticket
 * #207) — the persistent, project-local storage boundary Review Replay
 * (#164) will read from. Mirrors {@code ProjectMemoryStoreTest}'s own
 * conventions for the same kind of {@code .athena/}-rooted store.
 */
class ReviewRecordingArtifactStoreTest {

    private static final Instant START = Instant.parse("2026-09-17T10:00:00Z");

    private Path projectRoot;
    private ReviewRecordingArtifactStore store;

    @BeforeEach
    void createProject() throws IOException {
        projectRoot = Files.createTempDirectory("athena-review-recording-artifact-store-test-");
        store = new ReviewRecordingArtifactStore(projectRoot);
    }

    @AfterEach
    void cleanUpProject() {
        TempDirectories.deleteRecursively(projectRoot);
    }

    @Test
    void isEmptyForAnArtifactThatWasNeverPersisted() {
        assertThat(store.find("does-not-exist")).isEmpty();
    }

    @Test
    void persistsAnArtifactAndReadsItBackIntact() {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));
        recording.capture(SemanticEvent.of(SemanticEventType.ENTITY_INSPECTED, "entity:OrderService", START));
        Moment question = recording.tagMoment(MomentKind.QUESTION);
        recording.confirmMoment(question.id());
        recording.stop();
        ReviewRecordingArtifact artifact = ReviewRecordingArtifact.of(recording);

        store.persist(artifact);
        Optional<ReviewRecordingArtifact> reopened = store.find(recording.id());

        assertThat(reopened).isPresent();
        assertThat(reopened.get().recordingId()).isEqualTo(recording.id());
        assertThat(reopened.get().repositoryFullName()).isEqualTo("acme/widgets");
        assertThat(reopened.get().pullRequestNumber()).isEqualTo(42);
        assertThat(reopened.get().commitOrVersion()).isEqualTo("abc123");
        assertThat(reopened.get().events()).hasSize(1);
        assertThat(reopened.get().moments()).hasSize(1);
        assertThat(reopened.get().summary().momentCountsByKind()).containsEntry(MomentKind.QUESTION, 1);
    }

    @Test
    void persistingTwoArtifactsPreservesBoth() {
        ReviewRecording first = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));
        first.stop();
        ReviewRecording second = ReviewRecording.start("acme/widgets", 43, "def456", "Maria",
                Clock.fixed(START, ZoneOffset.UTC));
        second.stop();

        store.persist(ReviewRecordingArtifact.of(first));
        store.persist(ReviewRecordingArtifact.of(second));

        assertThat(store.find(first.id())).isPresent();
        assertThat(store.find(second.id())).isPresent();
    }

    @Test
    void aCorruptArtifactFileFailsLoudlyRatherThanSilentlyReturningEmpty() throws IOException {
        Path artifactsDir = projectRoot.resolve(".athena").resolve("review-recordings");
        Files.createDirectories(artifactsDir);
        Files.writeString(artifactsDir.resolve("broken.json"), "not valid json");

        assertThatThrownBy(() -> store.find("broken")).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void importsASharedArtifactFileForTheMatchingRepository() throws IOException {
        Path exported = exportedArtifactFile("acme/widgets");

        ReviewRecordingArtifact imported = store.importFrom(exported, "acme/widgets");

        assertThat(imported.repositoryFullName()).isEqualTo("acme/widgets");
        assertThat(store.find(imported.recordingId())).isPresent();
    }

    @Test
    void rejectsImportingAnArtifactForADifferentRepository() throws IOException {
        Path exported = exportedArtifactFile("acme/widgets");

        assertThatThrownBy(() -> store.importFrom(exported, "other-org/other-repo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsImportingAMalformedFile() throws IOException {
        Path malformed = Files.createTempFile("athena-review-recording-shared-artifact-", ".json");
        Files.writeString(malformed, "not valid json");

        assertThatThrownBy(() -> store.importFrom(malformed, "acme/widgets"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsImportingValidJsonWithACorruptFieldValue() throws IOException {
        Path validJsonBadTimestamp = Files.createTempFile("athena-review-recording-shared-artifact-", ".json");
        Files.writeString(validJsonBadTimestamp, """
                {
                  "recordingId": "r1",
                  "repositoryFullName": "acme/widgets",
                  "pullRequestNumber": 42,
                  "commitOrVersion": "abc123",
                  "durationSeconds": 0,
                  "events": [{"type": "ENTITY_INSPECTED", "reference": "entity:OrderService", "occurredAt": "not-a-timestamp"}],
                  "moments": []
                }
                """);

        assertThatThrownBy(() -> store.importFrom(validJsonBadTimestamp, "acme/widgets"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsImportingAnArtifactWhoseRecordingIdAlreadyExistsLocally() throws IOException {
        ReviewRecording recording = ReviewRecording.start("acme/widgets", 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));
        recording.stop();
        store.persist(ReviewRecordingArtifact.of(recording));
        Path exported = exportedArtifactFileFor(recording);

        assertThatThrownBy(() -> store.importFrom(exported, "acme/widgets"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Path exportedArtifactFile(String repositoryFullName) throws IOException {
        ReviewRecording recording = ReviewRecording.start(repositoryFullName, 42, "abc123", "Petros",
                Clock.fixed(START, ZoneOffset.UTC));
        recording.stop();
        return exportedArtifactFileFor(recording);
    }

    private Path exportedArtifactFileFor(ReviewRecording recording) throws IOException {
        Path exportsDir = Files.createTempDirectory("athena-review-recording-shared-artifact-");
        ReviewRecordingArtifactStore exportingStore = new ReviewRecordingArtifactStore(exportsDir);
        exportingStore.persist(ReviewRecordingArtifact.of(recording));
        return exportsDir.resolve(".athena").resolve("review-recordings").resolve(recording.id() + ".json");
    }
}
