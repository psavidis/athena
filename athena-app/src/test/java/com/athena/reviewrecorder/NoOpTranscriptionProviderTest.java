package com.athena.reviewrecorder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Dedicated unit test for {@link NoOpTranscriptionProvider} (ticket #208). */
class NoOpTranscriptionProviderTest {

    @Test
    void neverProducesATranscript() {
        TranscriptionProvider provider = new NoOpTranscriptionProvider();

        assertThat(provider.transcribe()).isEmpty();
    }
}
