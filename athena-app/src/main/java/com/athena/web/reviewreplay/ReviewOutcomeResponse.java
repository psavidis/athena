package com.athena.web.reviewreplay;

import com.athena.reviewreplay.ReviewOutcome;

import java.util.List;

/** A read-only view of a {@link ReviewOutcome} (ticket #215). */
public record ReviewOutcomeResponse(
        List<OutcomeItemResponse> learned,
        List<OutcomeItemResponse> decided,
        List<OutcomeItemResponse> unresolved,
        List<OutcomeItemResponse> actions) {

    static ReviewOutcomeResponse of(ReviewOutcome outcome) {
        return new ReviewOutcomeResponse(
                outcome.learned().stream().map(OutcomeItemResponse::of).toList(),
                outcome.decided().stream().map(OutcomeItemResponse::of).toList(),
                outcome.unresolved().stream().map(OutcomeItemResponse::of).toList(),
                outcome.actions().stream().map(OutcomeItemResponse::of).toList());
    }
}
