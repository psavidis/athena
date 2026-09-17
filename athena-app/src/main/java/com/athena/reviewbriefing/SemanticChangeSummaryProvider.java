package com.athena.reviewbriefing;

import com.athena.semantic.Change;
import com.athena.semantic.SemanticProfile;

import java.util.List;

/**
 * The seam between "what changed, in plain language?" and an actual AI
 * provider (ticket #219) — mirrors {@code
 * com.athena.ai.ModuleNarrativeProvider}'s role for module narratives,
 * summarizing a PR's whole set of detected Changes (with their
 * seven-dimension {@link SemanticProfile}s) rather than one module's.
 * Advisory only: the returned summary is never authoritative — the
 * human reviewer's own reading of the diff remains the source of truth.
 */
public interface SemanticChangeSummaryProvider {

    /**
     * A 1-2 sentence, plain-language explanation of what {@code changes}
     * accomplish together, drawing on their {@code profiles} (affected
     * components, architectural layers, responsibility shifts) rather
     * than file/line counts. {@code profiles} is in the same order as
     * {@code changes}, one per Change (see {@code AnalysisResult#semanticProfileFor}).
     */
    String summarize(List<Change> changes, List<SemanticProfile> profiles);
}
