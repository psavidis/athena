package com.athena.reviewrecorder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Installs a real, pinned WhisperX + pyannote.audio pipeline into a fresh
 * virtual environment for tests (ticket #251) — never mocked, per
 * CODE_STYLE.md/qa-ticket's Detroit-school rule, matching {@code
 * com.athena.plugins.EslintTestFixture}'s own precedent for a real
 * external tool installed once per test class rather than per test.
 *
 * <p>Requires a Python interpreter &le;3.13 on {@code PATH} as {@code
 * python3.12} (WhisperX's pinned {@code ctranslate2} dependency has no
 * wheel for 3.14+ — confirmed during #250's investigation) and a
 * Hugging Face token with access to whichever diarization model the
 * installed WhisperX version requests, available via the standard {@code
 * huggingface_hub} credential file ({@code ~/.cache/huggingface/token})
 * or the {@code HF_TOKEN}/{@code HUGGINGFACE_HUB_TOKEN} environment
 * variable. Tests using this fixture are skipped (not failed) when
 * neither is available — see {@link #isAvailable()} — since CI/a fresh
 * checkout won't have this set up without deliberate provisioning,
 * unlike ESLint's {@code npm install} which needs no prior gated access.
 */
final class WhisperXTestFixture {

    private static final String PYTHON_INTERPRETER = "python3.12";

    private WhisperXTestFixture() {
    }

    /**
     * Whether this machine can plausibly run WhisperX tests at all: a compatible Python
     * interpreter on {@code PATH}, and some Hugging Face credential available. Does not
     * guarantee the install/model download will succeed (network, disk, gated-model-access
     * issues can still surface as {@link #installInto} failing) — this is the cheap
     * pre-check tests use to skip cleanly rather than fail noisily in an unprovisioned
     * environment.
     */
    static boolean isAvailable() {
        return interpreterAvailable() && huggingFaceTokenAvailable();
    }

    private static boolean interpreterAvailable() {
        try {
            Process process = new ProcessBuilder(PYTHON_INTERPRETER, "--version").start();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private static boolean huggingFaceTokenAvailable() {
        if (System.getenv("HF_TOKEN") != null || System.getenv("HUGGINGFACE_HUB_TOKEN") != null) {
            return true;
        }
        Path defaultTokenFile = Path.of(System.getProperty("user.home"), ".cache", "huggingface", "token");
        return Files.isRegularFile(defaultTokenFile);
    }

    /** Installs a real WhisperX + its dependencies into {@code venvRoot} via a real, network-touching pip install. */
    static Path installInto(Path venvRoot) throws IOException, InterruptedException {
        runOrThrow(new ProcessBuilder(PYTHON_INTERPRETER, "-m", "venv", venvRoot.toString()), 2);
        Path pip = venvRoot.resolve("bin").resolve("pip");
        runOrThrow(new ProcessBuilder(pip.toString(), "install", "--quiet", "whisperx"), 10);
        return venvRoot.resolve("bin").resolve("python3");
    }

    private static void runOrThrow(ProcessBuilder processBuilder, int timeoutMinutes) throws IOException, InterruptedException {
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("timed out installing a real WhisperX pipeline for testing");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("could not install a real WhisperX pipeline for testing: " + output);
        }
    }
}
