package com.athena.reviewcontext;

/**
 * Whether a review has been explicitly confirmed and submitted. Mirrors
 * {@link com.athena.reviewui.ReviewCompletion}'s pattern: nothing but an
 * explicit reviewer confirmation can make {@link #isSubmitted()} true
 * (epic #6 §32 — "the reviewer explicitly confirms submission").
 */
public final class ReviewSubmission {

    private boolean submitted = false;

    /** The only way a submission is sent: an explicit reviewer confirmation. */
    public void confirmAndSubmit() {
        submitted = true;
    }

    public boolean isSubmitted() {
        return submitted;
    }
}
