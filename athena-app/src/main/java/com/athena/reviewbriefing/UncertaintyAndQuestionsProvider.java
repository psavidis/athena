package com.athena.reviewbriefing;

import com.athena.semantic.Change;
import com.athena.semantic.SemanticProfile;

import java.util.List;

/**
 * The seam between "what am I not confident about, and what should I
 * ask?" and an actual AI provider (ticket #221) — mirrors {@code
 * SemanticChangeSummaryProvider}'s (#219) own structure and the same
 * AI-provider pattern Context Rewind's narrative established. Advisory
 * only: every returned item is Athena's own inference, never presented
 * as fact — enforced structurally by living under {@link
 * ReviewBriefing#uncertainties()}/{@link ReviewBriefing#questions()}, not
 * by a text label this provider would have to remember to add.
 */
public interface UncertaintyAndQuestionsProvider {

    /**
     * Flags what {@code changes} (with their {@code profiles}) Athena can't confidently explain,
     * and suggests investigation questions — both may be empty for a confidently-explainable PR.
     * {@code profiles} is in the same order as {@code changes}, one per Change.
     */
    UncertaintyAndQuestions analyze(List<Change> changes, List<SemanticProfile> profiles);
}
