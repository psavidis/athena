package com.athena.memory;

import com.athena.repository.ChangedFile;
import com.athena.repository.ClosedPullRequestSummary;
import com.athena.repository.ImportedPullRequest;
import com.athena.repository.PullRequestSummary;
import com.athena.repository.RepositoryProvider;

import java.nio.file.Path;
import java.util.List;
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
 * <p>Re-running {@link #learn} only processes closed Pull Requests not
 * already processed on an earlier pass (ticket #174, via
 * {@link LearningProgressStore}'s marker set — Pull Requests don't
 * necessarily close in number order, unlike git's linear history, so a
 * set of already-processed numbers is used rather than a single cursor).
 * A pair's co-occurrence count accumulates across passes the same way
 * {@link GitHistoryLearner} does. Open Pull Requests are always
 * reprocessed in full on every pass — they are current, not-yet-settled
 * context, not historical evidence with a high-water mark.
 */
public final class PullRequestHistoryLearner {

    private static final int MINIMUM_RECURRING_PULL_REQUESTS = 2;
    private static final String SOURCE = "pr-history";

    private PullRequestHistoryLearner() {
    }

    public static void learn(Path projectRoot, String repositoryFullName, RepositoryProvider provider,
                              ProjectMemoryStore store) {
        LearningProgressStore progress = new LearningProgressStore(projectRoot);
        Set<String> alreadyProcessed = progress.processedMarkers(SOURCE);

        for (ClosedPullRequestSummary pullRequest : provider.closedPullRequests(repositoryFullName)) {
            String marker = String.valueOf(pullRequest.number());
            if (alreadyProcessed.contains(marker)) {
                continue;
            }
            List<String> files = changedFilePaths(provider, repositoryFullName, pullRequest.number());
            if (pullRequest.merged()) {
                forEachPair(files, (fileA, fileB) ->
                        recordIfRecurring(store, progress, UnorderedFilePair.of(fileA, fileB)));
            } else {
                recordRejectedChange(store, files, pullRequest.number());
            }
            progress.recordProcessedMarker(SOURCE, marker);
        }

        for (PullRequestSummary pullRequest : provider.openPullRequests(repositoryFullName)) {
            List<String> files = changedFilePaths(provider, repositoryFullName, pullRequest.number());
            recordOpenPullRequestContext(store, files);
        }
    }

    private static void recordIfRecurring(ProjectMemoryStore store, LearningProgressStore progress, UnorderedFilePair pair) {
        String key = pair.first() + "|" + pair.second();
        progress.incrementCounter(SOURCE, key, 1);
        int cumulativeCount = progress.counter(SOURCE, key);
        if (cumulativeCount < MINIMUM_RECURRING_PULL_REQUESTS) {
            return;
        }
        String fact = pair.first() + " and " + pair.second() + " were touched together across merged Pull Requests";
        store.record(new MemoryEntry(fact, cumulativeCount + " Pull Requests", confidenceFor(cumulativeCount), false));
    }

    private static void recordRejectedChange(ProjectMemoryStore store, List<String> files, int number) {
        forEachPair(files, (fileA, fileB) -> {
            UnorderedFilePair pair = UnorderedFilePair.of(fileA, fileB);
            String fact = pair.first() + " and " + pair.second() + " were proposed together in Pull Request "
                    + number + ", which was closed without merging";
            store.record(new MemoryEntry(fact, "Pull Request " + number, "low", false));
        });
    }

    private static void recordOpenPullRequestContext(ProjectMemoryStore store, List<String> files) {
        forEachPair(files, (fileA, fileB) -> {
            UnorderedFilePair pair = UnorderedFilePair.of(fileA, fileB);
            String fact = pair.first() + " and " + pair.second() + " were touched together in an open Pull Request";
            store.record(new MemoryEntry(fact, "open Pull Request", "provisional", false));
        });
    }

    private static List<String> changedFilePaths(RepositoryProvider provider, String repositoryFullName, int number) {
        ImportedPullRequest imported = provider.importPullRequest(repositoryFullName, number);
        return imported.changedFiles().stream().map(ChangedFile::path).collect(Collectors.toList());
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
