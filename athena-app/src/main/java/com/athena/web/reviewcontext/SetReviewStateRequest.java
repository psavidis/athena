package com.athena.web.reviewcontext;

import com.athena.semantic.ReviewState;

/** Request body for setting a Change's review state (ticket #76). */
public record SetReviewStateRequest(ReviewState state) {
}
