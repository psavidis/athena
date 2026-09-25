package com.athena.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Materializes a real file tree for a git revision by shelling out to the
 * system `git` binary — the minimal way to get a source tree a {@link
 * com.athena.semantic.spi.LanguagePlugin} can parse, since
 * neither GitHub's REST API nor a bundled JGit dependency is available
 * (ticket #65). Has no knowledge of GitHub or authentication: it works
 * against any git remote a caller's {@code environment} can authenticate
 * against, including a plain local repository path (as tests use). Shared
 * between {@code com.athena.cli} and {@code com.athena.web} (ticket #73).
 *
 * <p>Fetches the exact revision directly (shallow, depth 1) rather than
 * cloning the whole repository and checking out a revision from within
 * it. This matters for a real PR whose source branch has since been
 * deleted (the common case once a PR is squash-merged): a plain clone
 * only fetches reachable branches, so the PR's original head commit —
 * now unreachable from any branch — would be missing, even though GitHub
 * still serves that exact commit object when asked for it directly.
 *
 * <p>A fetch is bounded by {@link FetchLimits} (ticket #359): git aborts a transfer that stays
 * too slow, the whole process is killed after a timeout, and a transient network failure is
 * retried — a stalled or dropped connection must not hang or needlessly fail an analysis.
 */
public final class GitRevisionCheckout {

    private GitRevisionCheckout() {
    }

    /**
     * Fetches {@code revision} from {@code repositoryUrl} and checks it out into a fresh temp
     * directory under {@code parentDir}.
     *
     * @param environment extra environment variables for the `git` subprocess (e.g. a
     *                     {@code GIT_ASKPASS} credential helper); empty when none are needed
     */
    public static Path checkout(String repositoryUrl, String revision, Path parentDir, Map<String, String> environment) {
        return checkout(repositoryUrl, revision, parentDir, environment, FetchLimits.DEFAULT);
    }

    /** As {@link #checkout(String, String, Path, Map)}, with explicit {@code limits} for the fetch (ticket #359). */
    public static Path checkout(String repositoryUrl, String revision, Path parentDir, Map<String, String> environment,
                                FetchLimits limits) {
        Path dir;
        try {
            dir = Files.createTempDirectory(parentDir, "athena-checkout-");
        } catch (IOException e) {
            throw new GitCheckoutException("Could not create a temp directory under " + parentDir, e);
        }
        try {
            run(dir, environment, NO_TIMEOUT, "git", "init", "--quiet");
            fetch(dir, environment, limits, repositoryUrl, revision);
            run(dir, environment, NO_TIMEOUT, "git", "checkout", "--quiet", "FETCH_HEAD");
        } catch (RuntimeException e) {
            TempDirectories.deleteRecursively(dir);
            throw e;
        }
        return dir;
    }

    private static final Duration NO_TIMEOUT = Duration.ZERO;
    private static final Pattern TRANSIENT = Pattern.compile(
            "timed out|RPC failed|Connection reset|Operation too slow|remote end hung up|early EOF"
                    + "|unexpected disconnect|Could not resolve host|Failed to connect|curl \\d+", Pattern.CASE_INSENSITIVE);

    /** Fetches {@code revision}, retrying a transient failure up to {@code limits.retries()} times (ticket #359). */
    private static void fetch(Path dir, Map<String, String> environment, FetchLimits limits, String repositoryUrl,
                              String revision) {
        for (int attempt = 0; ; attempt++) {
            try {
                run(dir, environment, limits.timeout(), "git",
                        "-c", "http.lowSpeedLimit=" + limits.lowSpeedBytesPerSecond(),
                        "-c", "http.lowSpeedTime=" + limits.lowSpeedTime().toSeconds(),
                        "fetch", "--quiet", "--depth", "1", repositoryUrl, revision);
                return;
            } catch (GitCheckoutException e) {
                if (attempt >= limits.retries() || !isTransient(e.getMessage())) {
                    throw e;
                }
            }
        }
    }

    /** Whether a failed git command's message describes a network problem worth one more try (ticket #359). */
    static boolean isTransient(String message) {
        return message != null && TRANSIENT.matcher(message).find();
    }

    /** Runs {@code command}, killing it after {@code timeout} unless that is {@link #NO_TIMEOUT}. */
    private static void run(Path workingDir, Map<String, String> environment, Duration timeout, String... command) {
        String commandLine = String.join(" ", command);
        Path stderrFile;
        try {
            // Written to a file, not read from a pipe, so a hung process can't block the timeout.
            stderrFile = Files.createTempFile("athena-git-stderr-", ".log");
        } catch (IOException e) {
            throw new GitCheckoutException("Could not create a temp file for: " + commandLine, e);
        }
        String[] resolved = command.clone();
        resolved[0] = executable(command[0], environment);
        ProcessBuilder builder = new ProcessBuilder(resolved).directory(workingDir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(stderrFile.toFile());
        builder.environment().putAll(environment);
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        try {
            Process process;
            try {
                process = builder.start();
            } catch (IOException e) {
                throw new GitCheckoutException("Failed to run: " + commandLine, e);
            }
            try {
                if (timeout.isZero()) {
                    process.waitFor();
                } else if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    throw new GitCheckoutException(commandLine + " timed out after " + timeout.toSeconds() + " s");
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new GitCheckoutException("Interrupted while running: " + commandLine, e);
            }
            if (process.exitValue() != 0) {
                throw new GitCheckoutException(commandLine + " failed: " + readQuietly(stderrFile).strip());
            }
        } finally {
            TempDirectories.deleteRecursively(stderrFile);
        }
    }

    /**
     * {@code name} resolved on the {@code PATH} of {@code environment} when it sets one:
     * {@link ProcessBuilder} looks commands up on the parent process's {@code PATH}, which would
     * silently ignore the caller's.
     */
    private static String executable(String name, Map<String, String> environment) {
        String path = environment.get("PATH");
        if (path == null || name.contains("/")) {
            return name;
        }
        for (String dir : path.split(java.io.File.pathSeparator)) {
            Path candidate = Path.of(dir, name);
            if (Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return name;
    }

    private static String readQuietly(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            return "";
        }
    }
}
