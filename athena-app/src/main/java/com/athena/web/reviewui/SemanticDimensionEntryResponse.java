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
 */
public record SemanticDimensionEntryResponse(SemanticDimension dimension, String conceptName,
                                              String conceptDescription, boolean inferred, int confidencePercent,
                                              List<String> evidence) {
}
