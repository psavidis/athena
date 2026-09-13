package com.athena.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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
        Path dir;
        try {
            dir = Files.createTempDirectory(parentDir, "athena-checkout-");
        } catch (IOException e) {
            throw new GitCheckoutException("Could not create a temp directory under " + parentDir, e);
        }
        try {
            run(dir, environment, "git", "init", "--quiet");
            run(dir, environment, "git", "fetch", "--quiet", "--depth", "1", repositoryUrl, revision);
            run(dir, environment, "git", "checkout", "--quiet", "FETCH_HEAD");
        } catch (RuntimeException e) {
            TempDirectories.deleteRecursively(dir);
            throw e;
        }
        return dir;
    }

    private static void run(Path workingDir, Map<String, String> environment, String... command) {
        ProcessBuilder builder = new ProcessBuilder(command).directory(workingDir.toFile());
        builder.environment().putAll(environment);
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new GitCheckoutException("Failed to run: " + String.join(" ", command), e);
        }

        String stderr;
        int exitCode;
        try {
            stderr = new String(process.getErrorStream().readAllBytes());
            exitCode = process.waitFor();
        } catch (IOException e) {
            throw new GitCheckoutException("Failed to read output of: " + String.join(" ", command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitCheckoutException("Interrupted while running: " + String.join(" ", command), e);
        }

        if (exitCode != 0) {
            throw new GitCheckoutException(String.join(" ", command) + " failed: " + stderr.strip());
        }
    }
}
