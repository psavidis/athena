package com.athena.web.github;

import com.athena.github.ImportedReviewData;

import java.util.List;

/** {@link ImportedReviewData}, serialized for the frontend's flat, file-grouped review comment
 * list (ticket #192) — not a structured reasoning trail; see the ticket for why. */
public record PullRequestReviewResponse(List<ReviewCommentResponse> comments, List<ReviewVerdictResponse> reviews) {

    static PullRequestReviewResponse from(ImportedReviewData data) {
        List<ReviewCommentResponse> comments = data.comments().stream()
                .map(comment -> new ReviewCommentResponse(comment.author(), comment.body(), comment.path()))
                .toList();
        List<ReviewVerdictResponse> reviews = data.reviews().stream()
                .map(review -> new ReviewVerdictResponse(review.reviewer(), review.state()))
                .toList();
        return new PullRequestReviewResponse(comments, reviews);
    }
}
