package com.athena.memory;

import com.athena.knowledge.spi.KnowledgeItem;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Feeds already-retrieved {@link KnowledgeItem}s (ticket #118's
 * {@code KnowledgeRetriever}) into Athena's project memory (ticket #172):
 * each retrieved item becomes a memory fact, with its source note and
 * Knowledge Provider retained as provenance. Takes the retrieval result
 * directly rather than a {@code KnowledgeProvider}/query itself: what
 * counts as "relevant" for a given caller (a review's changed files, or a
 * project-wide interest) is that caller's concern, not this learner's —
 * see {@code KnowledgeQuery}. This also means the learner has no path back
 * to the user's knowledge base: learning from it is one-directional by
 * construction, not merely by convention.
 *
 * <p>Re-running {@link #learn} against the same retrieved items does not
 * duplicate a fact already recorded — facts already known are left alone
 * rather than re-recorded.
 */
public final class ExternalKnowledgeLearner {

    private ExternalKnowledgeLearner() {
    }

    public static void learn(List<KnowledgeItem> items, ProjectMemoryStore store) {
        Set<String> alreadyLearned = store.entries().stream()
                .map(MemoryEntry::fact)
                .collect(Collectors.toCollection(HashSet::new));

        for (KnowledgeItem item : items) {
            String fact = item.content();
            if (!alreadyLearned.add(fact)) {
                continue;
            }
            String evidence = item.providerId() + " note: \"" + item.title() + "\"";
            store.record(new MemoryEntry(fact, evidence, "medium", false));
        }
    }
}
