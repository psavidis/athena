package com.athena.semantic;

/**
 * How far a PR's semantic analysis got, per epic #4's graceful-degradation
 * requirement (§45): the caller must always know which fallback level was
 * reached, rather than analysis silently succeeding or silently vanishing.
 */
public enum AnalysisStatus {
    /** Every file parsed and resolved; full Semantic Change Model is available. */
    READY,
    /** Some files degraded to a symbol-aware or textual diff, but not all. */
    PARTIALLY_ANALYZED,
    /** No file could be parsed; only the traditional textual diff is available. */
    ANALYSIS_FAILED
}
