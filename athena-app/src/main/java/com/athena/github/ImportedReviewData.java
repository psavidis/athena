package com.athena.github;

import java.util.List;

/**
 * A Pull Request's existing review comments and review state, imported
 * before Athena adds anything new to the review.
 */
public record ImportedReviewData(List<ReviewComment> comments, List<Review> reviews) {
}
