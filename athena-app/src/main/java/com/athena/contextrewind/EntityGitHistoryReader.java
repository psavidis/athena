package com.athena.contextrewind;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads the dated git commit history behind a single file (ticket #161) —
 * this ticket's own minimal git-log reader, kept separate from
 * {@code com.athena.memory.GitHistoryLearner} (which learns file-pair
 * co-change patterns for project memory, not a single file's own dated
 * history). Reads directly from {@code projectRoot} via the system
 * {@code git} binary, mirroring {@code GitHistoryLearner}'s own approach
 * to the same boundary: {@code projectRoot} is expected to already be a
 * working git checkout.
 */
final class EntityGitHistoryReader {

    private static final char FIELD_SEPARATOR = '';

    private EntityGitHistoryReader() {
    }

    /**
     * The commits that touched {@code fileName}, oldest first. Returns an
     * empty list — never throws — when {@code projectRoot} isn't a git
     * repository or the file has no history there.
     *
     * <p>{@code fileName} is a bare name (e.g. {@code "PaymentProcessor.java"}),
     * not a repo-relative path, so it's first resolved against every
     * currently-tracked file ending in that name (via a glob pathspec) —
     * a plain {@code git log -- fileName} would otherwise only ever match
     * a file sitting directly at {@code projectRoot}, never one under a
     * normal package directory. {@code --follow} (rename tracking) is
     * applied per resolved path rather than to the glob itself, since git
     * rejects combining {@code --follow} with glob pathspecs.
     */
    static List<HistoricalActivity> read(Path projectRoot, String fileName) {
        List<HistoricalActivity> activities = new ArrayList<>();
        for (String resolvedPath : resolveTrackedPaths(projectRoot, fileName)) {
            activities.addAll(readCommitsForPath(projectRoot, resolvedPath));
        }
        activities.sort(Comparator.comparing(HistoricalActivity::occurredAt));
        return activities;
    }

    private static List<String> resolveTrackedPaths(Path projectRoot, String fileName) {
        String output = runGit(projectRoot, List.of("git", "ls-files", "--", ":(glob)**/" + fileName));
        return output.lines().filter(line -> !line.isBlank()).toList();
    }

    private static List<HistoricalActivity> readCommitsForPath(Path projectRoot, String repoRelativePath) {
        String output = runGit(projectRoot, List.of("git", "log", "--follow",
                "--format=%H" + FIELD_SEPARATOR + "%cI" + FIELD_SEPARATOR + "%s", "--", repoRelativePath));
        if (output.isBlank()) {
            return List.of();
        }

        List<HistoricalActivity> activities = new ArrayList<>();
        for (String line : output.lines().toList()) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split(String.valueOf(FIELD_SEPARATOR), 3);
            String shortSha = fields[0].substring(0, 7);
            Instant occurredAt = OffsetDateTime.parse(fields[1]).toInstant();
            String subject = fields[2];
            activities.add(new HistoricalActivity(subject + " (" + shortSha + ")", occurredAt));
        }
        return activities;
    }

    private static String runGit(Path projectRoot, List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command).directory(projectRoot.toFile());
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            return exitCode == 0 ? output : "";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading git history at " + projectRoot, e);
        }
    }
}
