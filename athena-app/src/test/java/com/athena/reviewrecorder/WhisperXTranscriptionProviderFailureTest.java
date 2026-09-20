package com.athena.reviewrecorder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WhisperXTranscriptionProvider}'s failure paths (ticket #251) —
 * split from {@link WhisperXTranscriptionProviderTest} deliberately:
 * that class is skipped when this machine has no real WhisperX install
 * (see {@link WhisperXTestFixture#isAvailable()}), but these tests need
 * no real install at all and must always run, the same way {@code
 * ESLintAnalysisProviderTest#eslintNotInstalledProducesAFailedResultRatherThanThrowing}
 * needs no real ESLint present.
 */
class WhisperXTranscriptionProviderFailureTest {

    private static final Instant RECORDING_STARTED_AT = Instant.parse("2026-09-17T10:00:00Z");

    @Test
    void aMissingAudioFileProducesNoSegmentsRatherThanThrowing(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.wav");
        WhisperXTranscriptionProvider provider = WhisperXTranscriptionProvider.forAudioFile(missing, RECORDING_STARTED_AT);

        assertThat(provider.segments()).isEmpty();
        assertThat(provider.transcribe()).isEmpty();
    }

    @Test
    void anUnavailableInterpreterProducesNoSegmentsRatherThanThrowing() throws URISyntaxException {
        Path audio = Path.of(getClass().getResource("/audio-fixtures/one_speaker.wav").toURI());
        WhisperXTranscriptionProvider provider = WhisperXTranscriptionProvider.forAudioFile(
                audio, RECORDING_STARTED_AT, "definitely-not-a-real-interpreter-binary", Optional.empty(), Optional.empty());

        assertThat(provider.segments()).isEmpty();
        assertThat(provider.transcribe()).isEmpty();
    }
}
