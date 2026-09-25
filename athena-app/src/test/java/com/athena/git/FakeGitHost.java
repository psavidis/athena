package com.athena.git;

import com.athena.web.diff.GitRepositoryFixture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;

/**
 * A real local repository behind a fake {@code git} on the {@code PATH} (ticket #359). The network
 * is the one boundary a test can't cross for real, so the fake stands in for the host's behavior
 * on {@code fetch} only — it stalls, or drops the first fetch — and runs the real git otherwise.
 * Every fetch's arguments are logged, one line each.
 */
final class FakeGitHost {

    enum Behavior { NORMAL, STALL, RESET_FIRST_FETCH }

    private final GitRepositoryFixture repository;
    private final Path bin;
    private final Path log;
    private final Behavior behavior;

    private FakeGitHost(GitRepositoryFixture repository, Path bin, Path log, Behavior behavior) {
        this.repository = repository;
        this.bin = bin;
        this.log = log;
        this.behavior = behavior;
    }

    static FakeGitHost create(Behavior behavior) {
        try {
            GitRepositoryFixture repository = GitRepositoryFixture.create();
            repository.write("README.md", "hello\n");
            repository.commit("initial");
            Path bin = Files.createTempDirectory("athena-fake-git-");
            Path log = bin.resolve("fetches.log");
            Files.createFile(log);
            String realGit = realGit();
            Path script = bin.resolve("git");
            Files.writeString(script, """
                    #!/bin/bash
                    if [[ " $* " == *" fetch "* ]]; then
                      echo "$*" >> "$FAKE_GIT_LOG"
                      attempts=$(wc -l < "$FAKE_GIT_LOG")
                      if [ "$FAKE_GIT_BEHAVIOR" = STALL ]; then exec sleep 60; fi
                      if [ "$FAKE_GIT_BEHAVIOR" = RESET_FIRST_FETCH ] && [ "$attempts" -eq 1 ]; then
                        echo "error: RPC failed; curl 56 Recv failure: Connection reset by peer" >&2
                        exit 128
                      fi
                    fi
                    exec "%s" "$@"
                    """.formatted(realGit));
            Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
            return new FakeGitHost(repository, bin, log, behavior);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String url() {
        return repository.directory().toString();
    }

    String headRevision() {
        return repository.commit("head");
    }

    /** The environment that puts the fake {@code git} first on the {@code PATH}. */
    Map<String, String> environment() {
        return Map.of("PATH", bin + ":" + System.getenv("PATH"), "FAKE_GIT_LOG", log.toString(),
                "FAKE_GIT_BEHAVIOR", behavior.name());
    }

    List<String> fetches() {
        try {
            return Files.readAllLines(log);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void delete() {
        repository.delete();
        TempDirectories.deleteRecursively(bin);
    }

    private static String realGit() throws IOException {
        for (String dir : System.getenv("PATH").split(":")) {
            Path candidate = Path.of(dir, "git");
            if (Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        throw new IOException("git not found on PATH");
    }
}
