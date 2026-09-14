package com.athena.web.reviewui;

import com.athena.semantic.SemanticDimension;

import java.util.List;

/**
 * One {@link SemanticDimension} classification of a Change (ticket #94):
 * the concept it was classified with, whether that classification is
 * Observed (deterministic) or Inferred (heuristic), a confidence value,
 * and the supporting evidence needed for #91 §9's "Show evidence"
 * affordance. A dimension a Change has more than one classification for
 * (e.g. Intent's primary inference plus lower-confidence alternatives,
 * ticket #93) contributes one entry per classification, in the same
 * primary-first order {@code SemanticProfile} already carries them in.
 *
 * @param beforeEvidenceCount for a Framework classification that represents a
 *        mechanism transition (ticket #97, e.g. Spring field-to-constructor
 *        injection), how many of {@code evidence}'s leading entries are the
 *        "before" mechanism's diff text — the rest are "after". Zero for every
 *        classification that isn't a transition (i.e. all of {@code evidence}
 *        is simply "the current mechanism", no before/after split).
 * @param filesTouched the distinct files this classification's evidence
 *        touches (ticket #130: the Semantic Canvas's Structure altitude
 *        shows one node per file, derived from this rather than re-deriving
 *        it from raw evidence text on the frontend).
 * @param groupedMoveCount how many individual per-Change classifications this entry
 *        folds together, for the two ways a Capability-level entry can represent more
 *        than one Change: a {@code capability-extraction} entry (see {@link
 *        com.athena.semantic.CapabilitySplitDetector}) folding several {@code
 *        move-responsibility} moves between the same two modules, or any other
 *        RESPONSIBILITY concept (e.g. {@code add-capability}) recurring across several
 *        Changes (see {@link com.athena.semantic.RepeatedClassificationGrouper}). Zero
 *        for every entry that represents just one Change, including a lone occurrence
 *        that didn't recur often enough to count as a group.
 */
public record SemanticDimensionEntryResponse(SemanticDimension dimension, String conceptName,
                                              String conceptDescription, boolean inferred, int confidencePercent,
                                              List<String> evidence, List<String> supportingConceptNames,
                                              int beforeEvidenceCount, List<String> filesTouched,
                                              int groupedMoveCount) {
}
