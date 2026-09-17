package com.athena.web.reviewbriefing;

import com.athena.reviewbriefing.ReviewBriefing;
import com.athena.semantic.Change;

import java.util.List;

/**
 * A read-only view of a composed {@link ReviewBriefing} (ticket #223),
 * with each item's module resolved for canvas navigation (ticket #224).
 */
public record ReviewBriefingResponse(
        BriefingItemResponse changeSummary,
        List<BriefingItemResponse> focusAreas,
        List<BriefingItemResponse> uncertainties,
        List<BriefingItemResponse> questions,
        List<BriefingItemResponse> historicalContext,
        List<BriefingItemResponse> relevantKnowledge,
        BriefingItemResponse recommendedStartingPoint) {

    static ReviewBriefingResponse of(ReviewBriefing briefing, List<Change> changes) {
        return new ReviewBriefingResponse(
                briefing.changeSummary().map(item -> BriefingItemResponse.of(item, changes)).orElse(null),
                briefing.focusAreas().stream().map(item -> BriefingItemResponse.of(item, changes)).toList(),
                briefing.uncertainties().stream().map(item -> BriefingItemResponse.of(item, changes)).toList(),
                briefing.questions().stream().map(item -> BriefingItemResponse.of(item, changes)).toList(),
                briefing.historicalContext().stream().map(item -> BriefingItemResponse.of(item, changes)).toList(),
                briefing.relevantKnowledge().stream().map(item -> BriefingItemResponse.of(item, changes)).toList(),
                briefing.recommendedStartingPoint().map(item -> BriefingItemResponse.of(item, changes)).orElse(null));
    }
}
