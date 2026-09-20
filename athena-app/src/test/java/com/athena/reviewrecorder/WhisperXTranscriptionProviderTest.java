package com.athena.reviewrecorder;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated unit test for {@link WhisperXTranscriptionProvider} (ticket
 * #251). Runs the real WhisperX + pyannote.audio pipeline against
 * committed real audio fixtures (never mocked — see {@link
 * WhisperXTestFixture}'s javadoc for why, matching {@code
 * ESLintAnalysisProviderTest}'s own precedent for a real subprocess of a
 * real installed tool) — skipped entirely, not failed, when this
 * machine isn't provisioned with a compatible Python interpreter and
 * Hugging Face credentials (see {@link WhisperXTestFixture#isAvailable()}).
 * Failure-path tests that need no real install live in {@link
 * WhisperXTranscriptionProviderFailureTest} instead, so they always run.
 */
@EnabledIf("com.athena.reviewrecorder.WhisperXTestFixture#isAvailable")
class WhisperXTranscriptionProviderTest {

    private static final Instant RECORDING_STARTED_AT = Instant.parse("2026-09-17T10:00:00Z");

    private static Path venvPython;
    private static Path venvRoot;

    @BeforeAll
    static void installWhisperXOnce() throws IOException, InterruptedException {
        venvRoot = Files.createTempDirectory("athena-whisperx-test-venv-");
        venvPython = WhisperXTestFixture.installInto(venvRoot);
    }

    @AfterAll
    static void cleanUpVenv() throws IOException {
        if (venvRoot != null) {
            try (var walk = Files.walk(venvRoot)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
    }

    @Test
    void aTwoSpeakerRecordingIsTranscribedWithDistinctSpeakerAttribution() throws URISyntaxException {
        Path audio = fixtureAudio("two_speakers.wav");
        WhisperXTranscriptionProvider provider = WhisperXTranscriptionProvider.forAudioFile(
                audio, RECORDING_STARTED_AT, venvPython.toString(), Optional.of(2), Optional.of(2));

        List<TranscriptSegment> segments = provider.segments();

        assertThat(segments).hasSizeGreaterThanOrEqualTo(2);
        assertThat(segments).extracting(s -> s.speaker().orElse(null)).doesNotContainNull();
        assertThat(segments.stream().map(s -> s.speaker().orElseThrow()).distinct().count())
                .as("two distinct speakers should be identified")
                .isEqualTo(2);
    }

    @Test
    void aSingleSpeakerRecordingIsTranscribed() throws URISyntaxException {
        Path audio = fixtureAudio("one_speaker.wav");
        WhisperXTranscriptionProvider provider = WhisperXTranscriptionProvider.forAudioFile(
                audio, RECORDING_STARTED_AT, venvPython.toString(), Optional.empty(), Optional.empty());

        List<TranscriptSegment> segments = provider.segments();

        assertThat(segments).isNotEmpty();
        assertThat(segments.get(0).text()).containsIgnoringCase("execute");
    }

    @Test
    void segmentsAreAnchoredToTheRecordingsStartInstant() throws URISyntaxException {
        Path audio = fixtureAudio("one_speaker.wav");
        WhisperXTranscriptionProvider provider = WhisperXTranscriptionProvider.forAudioFile(
                audio, RECORDING_STARTED_AT, venvPython.toString(), Optional.empty(), Optional.empty());

        List<TranscriptSegment> segments = provider.segments();

        assertThat(segments).isNotEmpty();
        assertThat(segments.get(0).spokenAt()).isAfterOrEqualTo(RECORDING_STARTED_AT);
    }

    @Test
    void transcribeJoinsAllSegmentsIntoOneString() throws URISyntaxException {
        Path audio = fixtureAudio("one_speaker.wav");
        WhisperXTranscriptionProvider provider = WhisperXTranscriptionProvider.forAudioFile(
                audio, RECORDING_STARTED_AT, venvPython.toString(), Optional.empty(), Optional.empty());

        assertThat(provider.transcribe()).isPresent();
        assertThat(provider.transcribe().get()).containsIgnoringCase("execute");
    }

    private Path fixtureAudio(String fileName) throws URISyntaxException {
        return Path.of(getClass().getResource("/audio-fixtures/" + fileName).toURI());
    }
}
