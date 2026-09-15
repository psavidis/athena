package com.athena.memory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Learns historically co-changed files from a project's own git commit
 * history (ticket #170) and records each recurring pair as a
 * {@link MemoryEntry} through {@link ProjectMemoryStore} (ticket #169),
 * with the number of commits behind it as provenance. A pair that only
 * co-occurred once is coincidence, not a pattern, and is not recorded.
 *
 * <p>Reads history directly from {@code projectRoot} via the system
 * {@code git} binary, rather than through
 * {@link com.athena.git.GitRevisionCheckout} (a shallow, single-revision,
 * ephemeral fetch — see its Javadoc) or
 * {@link com.athena.repository.RepositoryProvider} (exposes no
 * commit-history listing today): {@code projectRoot} is expected to
 * already be a full, non-shallow local git working copy of the project
 * being learned from. Obtaining that copy from a hosting provider is a
 * separate concern, left to the caller (see {@link ProjectMemoryStore}'s
 * own Javadoc on the same boundary).
 *
 * <p>Re-running {@link #learn} against the same history does not
 * duplicate a pair already recorded — a full history walk is repeated
 * each time (incremental learning is out of scope, see ticket #170), but
 * facts already known are left alone rather than re-recorded.
 */
public final class GitHistoryLearner {

    private static final int MINIMUM_RECURRING_COMMITS = 2;

    private GitHistoryLearner() {
    }

    public static void learn(Path projectRoot, ProjectMemoryStore store) {
        Map<UnorderedFilePair, Integer> coChangeCounts = countCoChanges(readCommits(projectRoot));
        Set<String> alreadyLearned = store.entries().stream()
                .map(MemoryEntry::fact)
                .collect(Collectors.toSet());

        coChangeCounts.forEach((pair, count) -> {
            if (count < MINIMUM_RECURRING_COMMITS) {
                return;
            }
            String fact = pair.first() + " and " + pair.second() + " change together";
            if (alreadyLearned.contains(fact)) {
                return;
            }
            store.record(new MemoryEntry(fact, count + " commits", confidenceFor(count), false));
        });
    }

    private static String confidenceFor(int commitCount) {
        if (commitCount >= 5) {
            return "high";
        }
        if (commitCount >= 3) {
            return "medium";
        }
        return "low";
    }

    private static Map<UnorderedFilePair, Integer> countCoChanges(List<List<String>> commits) {
        Map<UnorderedFilePair, Integer> counts = new LinkedHashMap<>();
        for (List<String> filesInCommit : commits) {
            for (int i = 0; i < filesInCommit.size(); i++) {
                for (int j = i + 1; j < filesInCommit.size(); j++) {
                    UnorderedFilePair pair = UnorderedFilePair.of(filesInCommit.get(i), filesInCommit.get(j));
                    counts.merge(pair, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private static List<List<String>> readCommits(Path projectRoot) {
        String output = runGitLog(projectRoot);
        if (output.isEmpty()) {
            return List.of();
        }
        List<List<String>> commits = new ArrayList<>();
        for (String block : output.split("\u0000")) {
            List<String> files = block.lines().filter(line -> !line.isBlank()).collect(Collectors.toList());
            if (!files.isEmpty()) {
                commits.add(files);
            }
        }
        return commits;
    }

    private static String runGitLog(Path projectRoot) {
        ProcessBuilder builder = new ProcessBuilder("git", "log", "--name-only", "--pretty=format:%x00")
                .directory(projectRoot.toFile());
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
