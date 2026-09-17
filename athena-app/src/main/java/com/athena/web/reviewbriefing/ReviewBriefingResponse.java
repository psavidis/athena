package com.athena.web.reviewbriefing;

import com.athena.reviewbriefing.ReviewBriefing;

import java.util.List;

/** A read-only view of a composed {@link ReviewBriefing} (ticket #223). */
public record ReviewBriefingResponse(
        BriefingItemResponse changeSummary,
        List<BriefingItemResponse> focusAreas,
        List<BriefingItemResponse> uncertainties,
        List<BriefingItemResponse> questions,
        List<BriefingItemResponse> historicalContext,
        List<BriefingItemResponse> relevantKnowledge,
        BriefingItemResponse recommendedStartingPoint) {

    static ReviewBriefingResponse of(ReviewBriefing briefing) {
        return new ReviewBriefingResponse(
                briefing.changeSummary().map(BriefingItemResponse::of).orElse(null),
                briefing.focusAreas().stream().map(BriefingItemResponse::of).toList(),
                briefing.uncertainties().stream().map(BriefingItemResponse::of).toList(),
                briefing.questions().stream().map(BriefingItemResponse::of).toList(),
                briefing.historicalContext().stream().map(BriefingItemResponse::of).toList(),
                briefing.relevantKnowledge().stream().map(BriefingItemResponse::of).toList(),
                briefing.recommendedStartingPoint().map(BriefingItemResponse::of).orElse(null));
    }
}
