package com.athena.reviewrecorder;

import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/** Dedicated unit test for {@link ReviewRecordingRegistry} (ticket #203). */
class ReviewRecordingRegistryTest {

    private final ReviewRecordingRegistry registry = new ReviewRecordingRegistry(Clock.systemUTC());

    @Test
    void aStartedRecordingCanBeFoundById() {
        ReviewRecording recording = registry.start("acme/widgets", 42, "abc123", "Petros");

        assertThat(registry.find(recording.id())).contains(recording);
    }

    @Test
    void anUnknownIdIsNotFound() {
        assertThat(registry.find("does-not-exist")).isEmpty();
    }

    @Test
    void aStoppedRecordingCanNoLongerBeFoundAsActive() {
        ReviewRecording recording = registry.start("acme/widgets", 42, "abc123", "Petros");

        registry.stop(recording.id());

        assertThat(recording.active()).isFalse();
    }

    @Test
    void twoRecordingsGetDistinctIds() {
        ReviewRecording first = registry.start("acme/widgets", 42, "abc123", "Petros");
        ReviewRecording second = registry.start("acme/widgets", 43, "def456", "Maria");

        assertThat(first.id()).isNotEqualTo(second.id());
    }
}
