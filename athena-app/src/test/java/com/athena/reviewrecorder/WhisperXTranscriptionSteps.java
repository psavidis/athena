package com.athena.reviewrecorder;

import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Step definitions for WhisperX-backed transcription (ticket #251). The
 * "shared microphone" scenarios exercise a real WhisperX install against
 * committed audio fixtures — see {@link WhisperXTranscriptionProviderTest}'s
 * javadoc for why this is never mocked. Skipped (via {@code assumeTrue},
 * not failed) when this machine lacks a compatible Python interpreter or
 * Hugging Face credentials, matching {@link WhisperXTestFixture#isAvailable()}.
 *
 * <p>Uses the same small {@code "tiny"} model as {@link
 * WhisperXTranscriptionProviderTest} rather than the production default —
 * see that class's javadoc for why.
 */
public class WhisperXTranscriptionSteps {

    private static final Instant RECORDING_STARTED_AT = Instant.parse("2026-09-17T10:00:00Z");
    private static final String TEST_MODEL = "tiny";

    private static Path venvPython;
    private static Path venvRoot;

    private List<TranscriptSegment> transcript;
    private RuntimeException failure;

    @BeforeAll
    public static void installWhisperXOnceForThisFeature() throws IOException, InterruptedException {
        if (!WhisperXTestFixture.isAvailable()) {
            return;
        }
        venvRoot = Files.createTempDirectory("athena-whisperx-cucumber-venv-");
        venvPython = WhisperXTestFixture.installInto(venvRoot);
    }

    @AfterAll
    public static void cleanUpVenv() throws IOException {
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

    @Given("an audio recording of two participants speaking, one after the other")
    public void an_audio_recording_of_two_participants_speaking() {
        assumeTrue(WhisperXTestFixture.isAvailable(), "WhisperX is not provisioned on this machine");
        transcribe("two_speakers.wav", Optional.of(2), Optional.of(2));
    }

    @Given("an audio recording of a single participant speaking")
    public void an_audio_recording_of_a_single_participant_speaking() {
        assumeTrue(WhisperXTestFixture.isAvailable(), "WhisperX is not provisioned on this machine");
        transcribe("one_speaker.wav", Optional.empty(), Optional.empty());
    }

    private void transcribe(String fixtureFileName, Optional<Integer> minSpeakers, Optional<Integer> maxSpeakers) {
        try {
            Path audio = Path.of(getClass().getResource("/audio-fixtures/" + fixtureFileName).toURI());
            WhisperXTranscriptionProvider provider = WhisperXTranscriptionProvider.forAudioFile(
                    audio, RECORDING_STARTED_AT, venvPython.toString(), TEST_MODEL, minSpeakers, maxSpeakers);
            transcript = provider.segments();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Given("a Review Recording with audio capture enabled")
    public void a_review_recording_with_audio_capture_enabled() {
        // The recording itself is not exercised through the web layer here (this feature
        // covers the transcription engine only, per #251's own scope note) — the audio-enabled
        // precondition is expressed by which TranscriptionProvider produces the transcript.
    }

    @Given("the transcription pipeline is unavailable")
    public void the_transcription_pipeline_is_unavailable() throws URISyntaxException {
        Path audio = Path.of(getClass().getResource("/audio-fixtures/one_speaker.wav").toURI());
        WhisperXTranscriptionProvider provider = WhisperXTranscriptionProvider.forAudioFile(
                audio, RECORDING_STARTED_AT, "definitely-not-a-real-interpreter-binary", Optional.empty(), Optional.empty());
        try {
            transcript = provider.segments();
        } catch (RuntimeException e) {
            failure = e;
        }
    }

    @When("the recording's transcript is produced")
    public void the_recordings_transcript_is_produced() {
        // No-op: each Given step above already produced `transcript` — this step exists so the
        // scenario reads naturally (capture, then produce transcript) even though this feature's
        // scope (per #251/#253) means the "capture" half is simulated by a fixture file, not a
        // live microphone.
    }

    @Then("the transcript includes what each participant said")
    public void the_transcript_includes_what_each_participant_said() {
        assertThat(transcript).hasSizeGreaterThanOrEqualTo(2);
    }

    @Then("each spoken segment is attributed to a distinct speaker")
    public void each_spoken_segment_is_attributed_to_a_distinct_speaker() {
        assertThat(transcript).extracting(s -> s.speaker().orElse(null)).doesNotContainNull();
        long distinctSpeakers = transcript.stream().map(s -> s.speaker().orElseThrow()).distinct().count();
        assertThat(distinctSpeakers).isEqualTo(2);
    }

    @Then("the transcript includes what that participant said")
    public void the_transcript_includes_what_that_participant_said() {
        assertThat(transcript).isNotEmpty();
    }

    @Then("the transcript is empty")
    public void the_transcript_is_empty() {
        assertThat(transcript).isEmpty();
    }

    // "no error occurs" is already defined by ReviewRecordingSessionSteps and reused as-is here
    // (both classes share the same Cucumber glue path) — but that class asserts against its own
    // `failure` field, which this class's transcription steps never touch. This step folds that
    // same check into "the underlying Review Recording remains intact" instead, under its own
    // distinct text, rather than colliding with the existing step definition.
    @Then("the underlying Review Recording remains intact")
    public void the_underlying_review_recording_remains_intact() {
        // A failed transcription attempt above must never have thrown past
        // WhisperXTranscriptionProvider's own boundary (ticket #207's "never corrupts the
        // recording" contract) — asserting no exception propagated is the observable proxy for
        // "the recording itself was never touched," since this feature doesn't wire a live
        // ReviewRecording (see the scope note on "a Review Recording with audio capture enabled").
        assertThat(failure).isNull();
    }
}
