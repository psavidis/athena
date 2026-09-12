package com.athena.ai;

import com.athena.reviewcontext.ReviewContext;

import java.util.List;

/**
 * The seam between the post-review AI analysis flow and an actual AI
 * provider (Claude, Gemini, ...). The default production implementation
 * ({@link ClaudeAiProvider}) makes real HTTP calls to the provider's API;
 * tests substitute a fake so they don't cross the network boundary.
 * Switching providers means supplying a different {@code AiProvider}
 * implementation — nothing about the {@link ReviewContext} shape or the
 * calling code changes (epic #7 §34).
 */
public interface AiProvider {

    /**
     * Analyzes a completed human review and returns candidate findings —
     * "you may have missed X" (§35) — each independently identifiable via
     * {@link AiFinding#id()}. Never mutates the given {@link ReviewContext}
     * or anything it was assembled from: the AI never controls review
     * state, never auto-applies anything, and never blocks submission.
     */
    List<AiFinding> analyze(ReviewContext reviewContext);
}
