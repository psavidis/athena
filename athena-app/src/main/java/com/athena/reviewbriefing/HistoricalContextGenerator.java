package com.athena.reviewbriefing;

import com.athena.contextrewind.ContextFact;
import com.athena.contextrewind.ContextRewindRequest;
import com.athena.contextrewind.ContextRewindService;
import com.athena.contextrewind.ContextSource;
import com.athena.contextrewind.ReconstructedContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates {@link ReviewBriefing#historicalContext()}/{@link
 * ReviewBriefing#relevantKnowledge()} for a PR's focus-area entities
 * (ticket #222) — a thin mapping over {@link ContextRewindService}'s
 * existing aggregation (ticket #161), not new data-gathering: reuses the
 * same git-history/PR/project-memory/Knowledge-Base sources Context
 * Rewind already combines for one entity, split by each fact's {@link
 * ContextSource} into this ticket's two sections.
 *
 * <p>Capped at {@value #MAX_FACTS_PER_ENTITY} facts per entity per
 * section — "a pointer, not a dump" (the ticket's own words), not every
 * fact Context Rewind can find.
 */
public class HistoricalContextGenerator {

    private static final int MAX_FACTS_PER_ENTITY = 2;

    private final ContextRewindService contextRewindService;

    public HistoricalContextGenerator(ContextRewindService contextRewindService) {
        this.contextRewindService = contextRewindService;
    }

    /**
     * @param focusAreaEntityNames the semantic entities a PR's focus areas (#220) reference,
     *                              in first-seen order, de-duplicated by the caller if needed
     * @param projectRoot           the project's working copy, for git history
     * @param repositoryFullName    the repository the PR is hosted on, for PR references
     */
    public HistoricalContext generate(List<String> focusAreaEntityNames, Path projectRoot, String repositoryFullName) {
        List<BriefingItem> historicalContext = new ArrayList<>();
        List<BriefingItem> relevantKnowledge = new ArrayList<>();

        for (String entityName : focusAreaEntityNames) {
            ReconstructedContext context = contextRewindService.reconstruct(
                    ContextRewindRequest.of(entityName, projectRoot, repositoryFullName));
            appendCapped(historicalContext, factsFor(context, ContextSource.HISTORY, entityName));
            appendCapped(relevantKnowledge, factsFor(context, ContextSource.KNOWLEDGE_BASE, entityName));
        }

        return new HistoricalContext(historicalContext, relevantKnowledge);
    }

    private List<BriefingItem> factsFor(ReconstructedContext context, ContextSource source, String entityName) {
        return context.facts().stream()
                .filter(fact -> fact.source() == source)
                .map(ContextFact::description)
                .map(description -> BriefingItem.of(description, entityName))
                .toList();
    }

    private void appendCapped(List<BriefingItem> items, List<BriefingItem> entityItems) {
        items.addAll(entityItems.stream().limit(MAX_FACTS_PER_ENTITY).toList());
    }
}
