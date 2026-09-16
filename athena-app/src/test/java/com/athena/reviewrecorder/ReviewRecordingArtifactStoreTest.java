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
}
