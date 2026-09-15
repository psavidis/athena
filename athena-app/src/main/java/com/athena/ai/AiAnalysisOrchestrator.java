package com.athena.ai;

import com.athena.knowledge.spi.KnowledgeItem;
import com.athena.memory.MemoryEntry;
import com.athena.reviewui.AnnotationBoard;
import com.athena.semantic.Change;
import com.athena.semantic.ReviewStateStore;

import java.util.List;

/**
 * The single entry point for triggering AI analysis (epic #7 #63): composes
 * {@link AiContextBoundary}, the configured {@link AiProvider}, and
 * {@link AiFindingsBoard} in that exact order, so a caller can never
 * accidentally send an unfiltered {@code ReviewContext} to the provider by
 * building one some other way.
 */
public final class AiAnalysisOrchestrator {

    private AiAnalysisOrchestrator() {
    }

    public static AiFindingsBoard trigger(String prTitle, List<Change> changes, ReviewStateStore store,
                                           AnnotationBoard board, AiProvider provider) {
        return trigger(prTitle, changes, store, board, provider, List.of());
    }

    /**
     * Triggers analysis with the given project knowledge (ticket #118) folded into the
     * {@link AiContextBoundary}'s payload — empty when no Knowledge Provider is configured.
     */
    public static AiFindingsBoard trigger(String prTitle, List<Change> changes, ReviewStateStore store,
                                           AnnotationBoard board, AiProvider provider, List<KnowledgeItem> knowledgeItems) {
        return trigger(prTitle, changes, store, board, provider, knowledgeItems, List.of());
    }

    /**
     * Triggers analysis with the given project knowledge (ticket #118) and relevant project
     * memory (ticket #173) both folded into the {@link AiContextBoundary}'s payload — empty
     * when there is none, the normal case for a project whose memory hasn't learned anything
     * relevant yet.
     */
    public static AiFindingsBoard trigger(String prTitle, List<Change> changes, ReviewStateStore store,
                                           AnnotationBoard board, AiProvider provider, List<KnowledgeItem> knowledgeItems,
                                           List<MemoryEntry> memoryEntries) {
        AiContextBoundary boundary = AiContextBoundary.assemble(prTitle, changes, store, board, knowledgeItems, memoryEntries);
        List<AiFinding> findings = provider.analyze(boundary.payload());
        return AiFindingsBoard.assemble(findings, changes);
    }
}
