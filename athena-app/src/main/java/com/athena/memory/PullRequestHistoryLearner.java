package com.athena.memory;

import com.athena.repository.ChangedFile;
import com.athena.repository.ClosedPullRequestSummary;
import com.athena.repository.ImportedPullRequest;
import com.athena.repository.PullRequestSummary;
import com.athena.repository.RepositoryProvider;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Learns from a project's Pull Requests (ticket #171), a richer evidence
 * source than git history alone (ticket #170): files that recur together
 * across merged Pull Requests become a co-change fact, exactly as with
 * git-commit co-change; a Pull Request closed without merging is recorded
 * as its own evidence — a change that was proposed but not accepted —
 * never conflated with an accepted pattern; and a still-open Pull
 * Request's content is recorded with a distinctly provisional confidence,
 * since it is current context, not yet established history.
 *
 * <p>Re-running {@link #learn} against the same set of Pull Requests does
 * not duplicate a fact already recorded — a full pass over the currently
 * available closed/open Pull Requests is repeated each time (incremental
 * re-learning is out of scope, see ticket #171), but facts already known
 * are left alone rather than re-recorded.
 */
public final class PullRequestHistoryLearner {

    private static final int MINIMUM_RECURRING_PULL_REQUESTS = 2;

    private PullRequestHistoryLearner() {
    }

    public static void learn(String repositoryFullName, RepositoryProvider provider, ProjectMemoryStore store) {
        Set<String> alreadyLearned = store.entries().stream()
                .map(MemoryEntry::fact)
                .collect(Collectors.toCollection(HashSet::new));

        Map<UnorderedFilePair, Integer> mergedCoChangeCounts = new LinkedHashMap<>();
        for (ClosedPullRequestSummary pullRequest : provider.closedPullRequests(repositoryFullName)) {
            List<String> files = changedFilePaths(provider, repositoryFullName, pullRequest.number());
            if (pullRequest.merged()) {
                accumulateCoChanges(files, mergedCoChangeCounts);
            } else {
                recordRejectedChange(store, alreadyLearned, files, pullRequest.number());
            }
        }
        recordMergedCoChanges(store, alreadyLearned, mergedCoChangeCounts);

        for (PullRequestSummary pullRequest : provider.openPullRequests(repositoryFullName)) {
            List<String> files = changedFilePaths(provider, repositoryFullName, pullRequest.number());
            recordOpenPullRequestContext(store, alreadyLearned, files);
        }
    }

    private static List<String> changedFilePaths(RepositoryProvider provider, String repositoryFullName, int number) {
        ImportedPullRequest imported = provider.importPullRequest(repositoryFullName, number);
        return imported.changedFiles().stream().map(ChangedFile::path).collect(Collectors.toList());
    }

    private static void accumulateCoChanges(List<String> files, Map<UnorderedFilePair, Integer> counts) {
        forEachPair(files, (fileA, fileB) -> counts.merge(UnorderedFilePair.of(fileA, fileB), 1, Integer::sum));
    }

    private static void recordMergedCoChanges(ProjectMemoryStore store, Set<String> alreadyLearned,
                                               Map<UnorderedFilePair, Integer> counts) {
        counts.forEach((pair, count) -> {
            if (count < MINIMUM_RECURRING_PULL_REQUESTS) {
                return;
            }
            String fact = pair.first() + " and " + pair.second() + " were touched together across merged Pull Requests";
            recordIfNew(store, alreadyLearned, fact, count + " Pull Requests", confidenceFor(count));
        });
    }

    private static void recordRejectedChange(ProjectMemoryStore store, Set<String> alreadyLearned,
                                              List<String> files, int number) {
        forEachPair(files, (fileA, fileB) -> {
            UnorderedFilePair pair = UnorderedFilePair.of(fileA, fileB);
            String fact = pair.first() + " and " + pair.second() + " were proposed together in Pull Request "
                    + number + ", which was closed without merging";
            recordIfNew(store, alreadyLearned, fact, "Pull Request " + number, "low");
        });
    }

    private static void recordOpenPullRequestContext(ProjectMemoryStore store, Set<String> alreadyLearned,
                                                       List<String> files) {
        forEachPair(files, (fileA, fileB) -> {
            UnorderedFilePair pair = UnorderedFilePair.of(fileA, fileB);
            String fact = pair.first() + " and " + pair.second() + " were touched together in an open Pull Request";
            recordIfNew(store, alreadyLearned, fact, "open Pull Request", "provisional");
        });
    }

    private static void recordIfNew(ProjectMemoryStore store, Set<String> alreadyLearned, String fact,
                                     String evidence, String confidence) {
        if (!alreadyLearned.add(fact)) {
            return;
        }
        store.record(new MemoryEntry(fact, evidence, confidence, false));
    }

    private static void forEachPair(List<String> files, BiConsumer<String, String> consumer) {
        for (int i = 0; i < files.size(); i++) {
            for (int j = i + 1; j < files.size(); j++) {
                consumer.accept(files.get(i), files.get(j));
            }
        }
    }

    private static String confidenceFor(int pullRequestCount) {
        if (pullRequestCount >= 5) {
            return "high";
        }
        if (pullRequestCount >= 3) {
            return "medium";
        }
        return "low";
    }
}
