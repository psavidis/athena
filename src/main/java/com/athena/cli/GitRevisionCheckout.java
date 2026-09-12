package com.athena.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Materializes a real file tree for a git revision by shelling out to the
 * system `git` binary — the minimal way to get a source tree
 * {@link com.athena.semantic.TransformationDetector} can parse, since
 * neither GitHub's REST API nor a bundled JGit dependency is available
 * (ticket #65). Has no knowledge of GitHub or authentication: it works
 * against any git remote a caller's {@code environment} can authenticate
 * against, including a plain local repository path (as tests use).
 */
final class GitRevisionCheckout {

    private GitRevisionCheckout() {
    }

    /**
     * Clones {@code repositoryUrl} and checks out {@code revision} into a fresh temp directory
     * under {@code parentDir}.
     *
     * @param environment extra environment variables for the `git` subprocess (e.g. a
     *                     {@code GIT_ASKPASS} credential helper); empty when none are needed
     */
    static Path checkout(String repositoryUrl, String revision, Path parentDir, Map<String, String> environment) {
        Path dir;
        try {
            dir = Files.createTempDirectory(parentDir, "athena-checkout-");
        } catch (IOException e) {
            throw new GitCheckoutException("Could not create a temp directory under " + parentDir, e);
        }
        try {
            run(parentDir, environment, "git", "clone", "--quiet", repositoryUrl, dir.toString());
            run(dir, environment, "git", "checkout", "--quiet", revision);
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
