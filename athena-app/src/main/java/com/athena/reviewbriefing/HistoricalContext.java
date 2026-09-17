package com.athena.reviewbriefing;

import java.util.List;

/**
 * A PR's historical and Knowledge Base context (ticket #222), split the
 * way {@link com.athena.contextrewind.ContextRewindService} already tags
 * its own facts: {@link com.athena.contextrewind.ContextSource#HISTORY}
 * facts here become {@link #historicalContext()}, {@code
 * KNOWLEDGE_BASE} facts become {@link #relevantKnowledge()}. Either list
 * may be empty — a focus-area entity with no history/knowledge
 * contributes nothing, not a placeholder entry.
 */
public record HistoricalContext(List<BriefingItem> historicalContext, List<BriefingItem> relevantKnowledge) {

    public HistoricalContext {
        historicalContext = List.copyOf(historicalContext);
        relevantKnowledge = List.copyOf(relevantKnowledge);
    }

    public static HistoricalContext none() {
        return new HistoricalContext(List.of(), List.of());
    }
}
