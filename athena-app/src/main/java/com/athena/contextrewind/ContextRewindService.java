package com.athena.contextrewind;

import com.athena.knowledge.KnowledgeRetriever;
import com.athena.knowledge.spi.KnowledgeItem;
import com.athena.knowledge.spi.KnowledgeProvider;
import com.athena.knowledge.spi.KnowledgeProviderConfiguration;
import com.athena.knowledge.spi.KnowledgeQuery;
import com.athena.memory.MemoryEntry;
import com.athena.memory.ProjectMemoryStore;
import com.athena.memory.RelevantMemoryRetriever;
import com.athena.repository.ChangedFile;
import com.athena.repository.ClosedPullRequestSummary;
import com.athena.repository.ImportedPullRequest;
import com.athena.repository.RepositoryProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reconstructs a code entity's context (ticket #161) by aggregating
 * Athena's existing sources of project history rather than inventing a
 * new one: git commit history ({@link EntityGitHistoryReader}), Pull
 * Requests ({@link RepositoryProvider}), and project memory
 * ({@link ProjectMemoryStore}, ticket #121) — plus an optional Knowledge
 * Provider (ticket #118) and an optional AI-generated narrative. None of
 * these sources is a hard dependency of another: any subset may be
 * absent, and the result still reflects whatever was actually found.
 *
 * <p>An entity is identified today by its simple class name, associated
 * with historical evidence via the file-name convention
 * {@code <entityName>.java} — the same file-name-based association
 * {@code GitHistoryLearner}/{@link RelevantMemoryRetriever} already use.
 * Tracking a stable identity across renames is a known limitation,
 * flagged back on ticket #161 rather than solved here.
 */
public final class ContextRewindService {

    private final RepositoryProvider repositoryProvider;
    private final ProjectMemoryStore memoryStore;
    private final Map<KnowledgeProvider, KnowledgeProviderConfiguration> knowledgeProviders;
    private final Optional<ContextNarrativeProvider> narrativeProvider;

    public ContextRewindService(RepositoryProvider repositoryProvider, ProjectMemoryStore memoryStore,
                                 Map<KnowledgeProvider, KnowledgeProviderConfiguration> knowledgeProviders,
                                 Optional<ContextNarrativeProvider> narrativeProvider) {
        this.repositoryProvider = repositoryProvider;
        this.memoryStore = memoryStore;
        this.knowledgeProviders = Map.copyOf(knowledgeProviders);
        this.narrativeProvider = narrativeProvider;
    }

    public ReconstructedContext reconstruct(ContextRewindRequest request) {
        String fileName = request.entityName() + ".java";

        List<HistoricalActivity> relevantActivity = relevantGitActivity(request, fileName);
        List<PullRequestReference> pullRequestReferences = touchingPullRequests(request, fileName);
        List<MemoryEntry> memoryFacts = RelevantMemoryRetriever.retrieve(memoryStore, List.of(fileName));
        List<KnowledgeItem> knowledgeItems = retrieveKnowledge(request, fileName);

        List<ContextFact> facts = new ArrayList<>();
        relevantActivity.forEach(activity -> facts.add(new ContextFact(activity.description(), ContextSource.HISTORY)));
        pullRequestReferences.forEach(reference -> facts.add(new ContextFact(
                "Pull Request " + reference.number() + " touched " + request.entityName(), ContextSource.HISTORY)));
        memoryFacts.forEach(entry -> facts.add(new ContextFact(entry.fact(), ContextSource.HISTORY)));
        knowledgeItems.forEach(item -> facts.add(new ContextFact(item.title(), ContextSource.KNOWLEDGE_BASE)));

        boolean hasHistory = !relevantActivity.isEmpty() || !pullRequestReferences.isEmpty() || !memoryFacts.isEmpty();

        ReconstructedContext.Builder builder = ReconstructedContext.builder(request.entityName());
        facts.forEach(builder::fact);
        relevantActivity.forEach(builder::historicalActivity);
        pullRequestReferences.forEach(builder::pullRequestReference);

        if (!hasHistory) {
            builder.insufficientHistoryMessage(
                    "Not enough historical information is available for " + request.entityName());
        } else {
            narrativeProvider.ifPresent(provider -> builder.aiNarrative(
                    provider.narrate(request.entityName(), facts.stream().map(ContextFact::description).toList())));
        }

        return builder.build();
    }

    private static List<HistoricalActivity> relevantGitActivity(ContextRewindRequest request, String fileName) {
        List<HistoricalActivity> activity = EntityGitHistoryReader.read(request.projectRoot(), fileName);
        return request.since()
                .map(since -> activity.stream().filter(entry -> entry.occurredAt().isAfter(since)).toList())
                .orElse(activity);
    }

    private List<PullRequestReference> touchingPullRequests(ContextRewindRequest request, String fileName) {
        List<PullRequestReference> references = new ArrayList<>();
        for (ClosedPullRequestSummary summary : repositoryProvider.closedPullRequests(request.repositoryFullName())) {
            ImportedPullRequest imported = repositoryProvider.importPullRequest(request.repositoryFullName(), summary.number());
            boolean touchesEntity = imported.changedFiles().stream()
                    .map(ChangedFile::path)
                    .anyMatch(path -> path.endsWith(fileName));
            if (touchesEntity) {
                references.add(new PullRequestReference(summary.number(), request.repositoryFullName()));
            }
        }
        return references;
    }

    private List<KnowledgeItem> retrieveKnowledge(ContextRewindRequest request, String fileName) {
        KnowledgeQuery query = KnowledgeQuery.of(request.repositoryFullName(), List.of(fileName), List.of());
        return new KnowledgeRetriever(knowledgeProviders).retrieve(query);
    }
}
