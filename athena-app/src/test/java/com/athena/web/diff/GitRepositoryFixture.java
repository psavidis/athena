package com.athena.web.diff;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A throwaway local git repository for building a standalone Diff's two revisions:
 * write files, commit, repeat, then hand the two commit SHAs to {@code DiffSelectionController}.
 */
public final class GitRepositoryFixture {

    private final Path directory;

    private GitRepositoryFixture(Path directory) {
        this.directory = directory;
    }

    public static GitRepositoryFixture create() {
        try {
            GitRepositoryFixture fixture = new GitRepositoryFixture(Files.createTempDirectory("athena-diff-fixture-"));
            fixture.run("git", "init", "--quiet");
            fixture.run("git", "config", "user.email", "test@example.com");
            fixture.run("git", "config", "user.name", "Test");
            return fixture;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path directory() {
        return directory;
    }

    public GitRepositoryFixture write(String relativePath, String content) {
        try {
            Path file = directory.resolve(relativePath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Commits everything written so far and returns the new commit's SHA. */
    public String commit(String message) {
        run("git", "add", "-A");
        run("git", "commit", "--quiet", "--allow-empty", "-m", message);
        return capture("git", "rev-parse", "HEAD").strip();
    }

    public void delete() {
        if (!Files.exists(directory)) return;
        try (var walk = Files.walk(directory)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void run(String... command) {
        try {
            Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
            if (process.waitFor() != 0) {
                throw new IllegalStateException("Command failed: " + String.join(" ", command) + "\n"
                        + new String(process.getErrorStream().readAllBytes()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private String capture(String... command) {
        try {
            Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            return output;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
