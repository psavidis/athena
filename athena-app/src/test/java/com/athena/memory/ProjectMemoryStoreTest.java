package com.athena.memory;

import com.athena.git.TempDirectories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit tests for {@link ProjectMemoryStore} (ticket #169) — the persistent,
 * project-local storage boundary every other Project Memory learning source builds on.
 */
class ProjectMemoryStoreTest {

    private Path projectRoot;
    private ProjectMemoryStore store;

    @BeforeEach
    void createProject() throws IOException {
        projectRoot = Files.createTempDirectory("athena-project-memory-store-test-");
        store = new ProjectMemoryStore(projectRoot);
    }

    @AfterEach
    void cleanUpProject() {
        TempDirectories.deleteRecursively(projectRoot);
    }

    @Test
    void isEmptyForAProjectThatHasNotLearnedAnythingYet() {
        assertThat(store.entries()).isEmpty();
    }

    @Test
    void recordsAFactWithItsEvidenceAndConfidenceAndReadsItBackIntact() {
        store.record(new MemoryEntry(
                "Order and OrderProjection frequently change together", "18 git commits", "high", false));

        MemoryEntry recorded = store.entries().get(0);

        assertThat(recorded.fact()).isEqualTo("Order and OrderProjection frequently change together");
        assertThat(recorded.evidence()).isEqualTo("18 git commits");
        assertThat(recorded.confidence()).isEqualTo("high");
    }

    @Test
    void recordingANewFactPreservesFactsAlreadyRecorded() {
        store.record(new MemoryEntry("first fact", "evidence 1", "high", false));
        store.record(new MemoryEntry("second fact", "evidence 2", "high", false));

        assertThat(store.entries()).extracting(MemoryEntry::fact).containsExactly("first fact", "second fact");
    }

    @Test
    void distinguishesAnInferredPatternFromADeveloperConfirmedFact() {
        store.record(new MemoryEntry("inferred pattern", "3 commits", "low", false));
        store.record(new MemoryEntry("confirmed fact", "developer note", "high", true));

        assertThat(store.entries())
                .filteredOn(entry -> entry.fact().equals("inferred pattern"))
                .extracting(MemoryEntry::developerConfirmed)
                .containsExactly(false);
        assertThat(store.entries())
                .filteredOn(entry -> entry.fact().equals("confirmed fact"))
                .extracting(MemoryEntry::developerConfirmed)
                .containsExactly(true);
    }

    @Test
    void keepsMemorySeparateBetweenDifferentProjects() throws IOException {
        Path otherProjectRoot = Files.createTempDirectory("athena-project-memory-store-test-other-");
        try {
            ProjectMemoryStore otherStore = new ProjectMemoryStore(otherProjectRoot);

            store.record(new MemoryEntry("only this project's fact", "evidence", "high", false));

            assertThat(otherStore.entries()).isEmpty();
        } finally {
            TempDirectories.deleteRecursively(otherProjectRoot);
        }
    }

    @Test
    void storesMemoryAtAPredictableLocationUnderTheProjectRoot() throws IOException {
        store.record(new MemoryEntry("a recorded fact", "evidence", "high", false));

        Path memoryDirectory = projectRoot.resolve(".athena").resolve("memory");
        assertThat(memoryDirectory).isDirectory();
        try (var contents = Files.list(memoryDirectory)) {
            assertThat(contents).isNotEmpty();
        }
    }

    @Test
    void returnsToAnEmptyErrorFreeStateAfterItsMemoryDirectoryIsDeleted() {
        store.record(new MemoryEntry("a recorded fact", "evidence", "high", false));

        TempDirectories.deleteRecursively(projectRoot.resolve(".athena").resolve("memory"));

        assertThat(store.entries()).isEmpty();
    }
}
