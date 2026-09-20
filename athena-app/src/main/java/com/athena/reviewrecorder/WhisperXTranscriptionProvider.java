package com.athena.reviewrecorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A {@link TranscriptionProvider} backed by a self-hosted WhisperX +
 * pyannote.audio pipeline (ticket #251, per #250's decision), diarizing a
 * single mixed mono audio stream — the in-person, single-shared-microphone
 * recording mode. Shells out to the committed {@code transcribe.py}
 * wrapper script, matching {@link com.athena.plugins.ESLintAnalysisProvider}'s
 * precedent for integrating an external tool as a subprocess: real
 * process, JSON output, a failure result rather than a thrown exception
 * for an ordinary failure (tool not installed, model unavailable, bad
 * audio). Unlike that precedent, stdout and stderr are kept separate here
 * rather than merged — WhisperX/pyannote's own logging is noisy on
 * stderr, and merging it would corrupt the JSON this class expects to
 * find on stdout alone.
 *
 * <p>No live audio capture is wired to this class yet: it transcribes
 * whatever {@link Path} it is given. In-person mic capture (ticket #253)
 * and remote-mode capture/upload (ticket #252) are what will eventually
 * supply that file — this class only turns an already-captured file into
 * {@link TranscriptSegment}s. Merging multiple participants' separately
 * transcribed remote-mode streams is {@link RemoteTranscriptMerger}, in
 * this same ticket (#251) — only the network wiring to reach it with
 * real per-participant audio is #252's scope.
 */
public final class WhisperXTranscriptionProvider implements TranscriptionProvider {

    private static final String SCRIPT_RESOURCE_PATH = "/transcription/transcribe.py";
    private static final long TIMEOUT_MINUTES = 10;

    /**
     * Drains a subprocess's stderr concurrently with this class reading its stdout — see
     * {@link #runScript()}'s javadoc comment for why reading the two pipes sequentially would
     * risk a deadlock. Virtual threads: this pool only ever blocks on process I/O, never on
     * CPU work, so an unbounded supply of them is cheap and never itself a bottleneck.
     */
    private static final ExecutorService STDERR_DRAIN_POOL = Executors.newVirtualThreadPerTaskExecutor();

    private final Path audioFile;
    private final Instant recordingStartedAt;
    private final String pythonExecutable;
    private final Optional<Integer> minSpeakers;
    private final Optional<Integer> maxSpeakers;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * A provider that will transcribe {@code audioFile} once {@link #segments()}/{@link
     * #transcribe()} is called. WhisperX reports each segment's timing as an offset in seconds
     * from the start of the audio file, not wall-clock time — {@code recordingStartedAt} anchors
     * those offsets to the same {@link ReviewRecording#startedAt()} clock {@link SemanticEvent}s
     * already use, so {@link TranscriptAligner} can compare them meaningfully.
     */
    public static WhisperXTranscriptionProvider forAudioFile(Path audioFile, Instant recordingStartedAt) {
        return new WhisperXTranscriptionProvider(audioFile, recordingStartedAt, "python3",
                Optional.empty(), Optional.empty());
    }

    /**
     * @param pythonExecutable overridable so a test can point at a specific interpreter (ticket
     *                         #251's Python environment requires Python &le;3.13 — see {@code
     *                         transcribe.py}'s own docstring) rather than whatever {@code python3}
     *                         resolves to on the running machine
     * @param minSpeakers      a known lower bound on speaker count, or {@link Optional#empty()} to
     *                         let the diarization model infer it — narrowing this improves
     *                         diarization accuracy when the participant count is already known
     *                         (e.g. from {@link ReviewRecording#participantCount()})
     * @param maxSpeakers      the corresponding upper bound, or {@link Optional#empty()}
     */
    public static WhisperXTranscriptionProvider forAudioFile(Path audioFile, Instant recordingStartedAt,
                                                               String pythonExecutable, Optional<Integer> minSpeakers,
                                                               Optional<Integer> maxSpeakers) {
        return new WhisperXTranscriptionProvider(audioFile, recordingStartedAt, pythonExecutable, minSpeakers, maxSpeakers);
    }

    private WhisperXTranscriptionProvider(Path audioFile, Instant recordingStartedAt, String pythonExecutable,
                                           Optional<Integer> minSpeakers, Optional<Integer> maxSpeakers) {
        this.audioFile = audioFile;
        this.recordingStartedAt = recordingStartedAt;
        this.pythonExecutable = pythonExecutable;
        this.minSpeakers = Objects.requireNonNull(minSpeakers, "minSpeakers");
        this.maxSpeakers = Objects.requireNonNull(maxSpeakers, "maxSpeakers");
    }

    @Override
    public Optional<String> transcribe() {
        List<TranscriptSegment> segments = segments();
        if (segments.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder text = new StringBuilder();
        for (TranscriptSegment segment : segments) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(segment.text());
        }
        return Optional.of(text.toString());
    }

    @Override
    public List<TranscriptSegment> segments() {
        if (!Files.isRegularFile(audioFile)) {
            return List.of();
        }
        try {
            String output = runScript();
            return parseSegments(output);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // Matching ESLintAnalysisProvider's own contract: an ordinary failure (script
            // missing, interpreter missing, model not downloaded, timeout) degrades to no
            // transcript rather than throwing — a failed transcription must never corrupt or
            // lose the underlying ReviewRecording (ticket #207's existing requirement).
            return List.of();
        }
    }

    private String runScript() throws IOException, InterruptedException {
        Path script = extractedScript();
        List<String> command = new ArrayList<>(
                List.of(pythonExecutable, script.toString(), audioFile.toString()));
        minSpeakers.ifPresent(value -> {
            command.add("--min-speakers");
            command.add(value.toString());
        });
        maxSpeakers.ifPresent(value -> {
            command.add("--max-speakers");
            command.add(value.toString());
        });

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        // Deliberately NOT redirectErrorStream(true): WhisperX/pyannote log noisily to
        // stderr, and this class's stdout-is-pure-JSON contract would break if merged.
        // Both pipes are still drained concurrently below (stderr on its own thread) —
        // reading only stdout here would deadlock once WhisperX's stderr output fills the
        // OS pipe buffer, since the child would then block writing to stderr while this
        // thread blocks reading stdout, and neither side would ever reach process exit.
        Process process = processBuilder.start();
        Future<String> stderr = STDERR_DRAIN_POOL.submit(() -> readFully(process.getErrorStream()));
        String stdout = readFully(process.getInputStream());
        boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("transcription timed out after " + TIMEOUT_MINUTES + " minutes");
        }
        if (process.exitValue() != 0) {
            String diagnostic = stderrOrEmpty(stderr);
            throw new IOException("transcribe.py exited with code " + process.exitValue()
                    + (diagnostic.isBlank() ? "" : ": " + diagnostic.strip()));
        }
        return stdout;
    }

    private static String readFully(InputStream in) throws IOException {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Best-effort: a failed stderr read must not mask the real failure it would explain. */
    private static String stderrOrEmpty(Future<String> stderr) {
        try {
            return stderr.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException e) {
            return "";
        }
    }

    private List<TranscriptSegment> parseSegments(String output) {
        JsonNode root;
        try {
            root = json.readTree(output);
        } catch (IOException e) {
            return List.of();
        }
        List<TranscriptSegment> segments = new ArrayList<>();
        for (JsonNode segment : root.path("segments")) {
            String text = segment.path("text").asText("");
            if (text.isBlank()) {
                continue;
            }
            long offsetMillis = Math.round(segment.path("start").asDouble(0) * 1000);
            Instant spokenAt = recordingStartedAt.plusMillis(offsetMillis);
            JsonNode speakerNode = segment.path("speaker");
            segments.add(speakerNode.isMissingNode() || speakerNode.asText().isBlank()
                    ? TranscriptSegment.withoutSpeaker(text, spokenAt)
                    : TranscriptSegment.of(text, spokenAt, speakerNode.asText()));
        }
        return segments;
    }

    /**
     * Extracts the committed {@code transcribe.py} classpath resource to a real filesystem
     * path once per JVM run — {@link ProcessBuilder} needs a real file, and this class's
     * script lives inside the packaged jar at runtime, not on disk directly.
     */
    private static Path extractedScript() throws IOException {
        Path extracted = ExtractedScriptHolder.PATH;
        if (extracted == null) {
            throw new IOException("could not extract transcribe.py from classpath resources");
        }
        return extracted;
    }

    /** Lazily extracts the script once, on first use, and reuses it for the rest of this JVM's lifetime. */
    private static final class ExtractedScriptHolder {
        static final Path PATH = extract();

        private static Path extract() {
            try (InputStream in = WhisperXTranscriptionProvider.class.getResourceAsStream(SCRIPT_RESOURCE_PATH)) {
                if (in == null) {
                    return null;
                }
                Path tempFile = Files.createTempFile("athena-transcribe-", ".py");
                tempFile.toFile().deleteOnExit();
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
                return tempFile;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
