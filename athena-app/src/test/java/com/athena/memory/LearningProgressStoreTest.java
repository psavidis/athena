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
 * Dedicated unit tests for {@link LearningProgressStore} (ticket #174) — the
 * general "high-water mark" mechanism every learning source tracks its
 * incremental progress through, independent of any one source's own
 * pattern-recognition logic.
 */
class LearningProgressStoreTest {

    private Path projectRoot;
    private LearningProgressStore progress;

    @BeforeEach
    void createProject() throws IOException {
        projectRoot = Files.createTempDirectory("athena-learning-progress-store-test-");
        progress = new LearningProgressStore(projectRoot);
    }

    @AfterEach
    void cleanUpProject() {
        TempDirectories.deleteRecursively(projectRoot);
    }

    @Test
    void hasNoCursorForASourceThatHasNeverRecordedOne() {
        assertThat(progress.cursor("git-history")).isEmpty();
    }

    @Test
    void recordsAndReadsBackACursor() {
        progress.recordCursor("git-history", "abc123");

        assertThat(progress.cursor("git-history")).contains("abc123");
    }

    @Test
    void aLaterCursorReplacesAnEarlierOneForTheSameSource() {
        progress.recordCursor("git-history", "abc123");

        progress.recordCursor("git-history", "def456");

        assertThat(progress.cursor("git-history")).contains("def456");
    }

    @Test
    void keepsCursorsSeparateBetweenSources() {
        progress.recordCursor("git-history", "abc123");

        assertThat(progress.cursor("pr-history")).isEmpty();
    }

    @Test
    void hasNoProcessedMarkersForASourceThatHasNeverRecordedAny() {
        assertThat(progress.processedMarkers("pr-history")).isEmpty();
    }

    @Test
    void recordsAndReadsBackProcessedMarkers() {
        progress.recordProcessedMarker("pr-history", "42");
        progress.recordProcessedMarker("pr-history", "43");

        assertThat(progress.processedMarkers("pr-history")).containsExactlyInAnyOrder("42", "43");
    }

    @Test
    void recordingTheSameMarkerTwiceDoesNotDuplicateIt() {
        progress.recordProcessedMarker("pr-history", "42");

        progress.recordProcessedMarker("pr-history", "42");

        assertThat(progress.processedMarkers("pr-history")).containsExactly("42");
    }

    @Test
    void aCounterStartsAtZero() {
        assertThat(progress.counter("git-history", "A.java|B.java")).isZero();
    }

    @Test
    void incrementingACounterAccumulatesAcrossCalls() {
        progress.incrementCounter("git-history", "A.java|B.java", 1);
        progress.incrementCounter("git-history", "A.java|B.java", 2);

        assertThat(progress.counter("git-history", "A.java|B.java")).isEqualTo(3);
    }

    @Test
    void keepsCountersSeparateByKeyAndSource() {
        progress.incrementCounter("git-history", "A.java|B.java", 5);

        assertThat(progress.counter("git-history", "C.java|D.java")).isZero();
        assertThat(progress.counter("pr-history", "A.java|B.java")).isZero();
    }

    @Test
    void survivesReloadingFromDisk() {
        progress.recordCursor("git-history", "abc123");
        progress.recordProcessedMarker("pr-history", "42");
        progress.incrementCounter("git-history", "A.java|B.java", 2);

        LearningProgressStore reloaded = new LearningProgressStore(projectRoot);

        assertThat(reloaded.cursor("git-history")).contains("abc123");
        assertThat(reloaded.processedMarkers("pr-history")).containsExactly("42");
        assertThat(reloaded.counter("git-history", "A.java|B.java")).isEqualTo(2);
    }
}
