package com.athena.reviewui;

/**
 * Whether a review has been explicitly marked complete by the reviewer.
 * Completion is always a distinct, confirmed human action — it is never
 * inferred from every Change reaching a terminal review state, however
 * complete coverage looks (epic #5 §41).
 */
public final class ReviewCompletion {

    private boolean complete = false;

    /** The only way a review becomes complete: an explicit reviewer confirmation. */
    public void confirmCompletion() {
        complete = true;
    }

    public boolean isComplete() {
        return complete;
    }
}
