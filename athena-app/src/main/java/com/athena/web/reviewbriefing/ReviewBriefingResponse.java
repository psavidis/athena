package com.athena.web.reviewbriefing;

import com.athena.reviewbriefing.ReviewBriefing;
import com.athena.semantic.Change;
import com.athena.semantic.ModuleGrouper;

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

    static ReviewBriefingResponse of(ReviewBriefing briefing, List<Change> changes, ModuleGrouper grouper) {
        return new ReviewBriefingResponse(
                briefing.changeSummary().map(item -> BriefingItemResponse.of(item, changes, grouper)).orElse(null),
                briefing.focusAreas().stream().map(item -> BriefingItemResponse.of(item, changes, grouper)).toList(),
                briefing.uncertainties().stream().map(item -> BriefingItemResponse.of(item, changes, grouper)).toList(),
                briefing.questions().stream().map(item -> BriefingItemResponse.of(item, changes, grouper)).toList(),
                briefing.historicalContext().stream().map(item -> BriefingItemResponse.of(item, changes, grouper)).toList(),
                briefing.relevantKnowledge().stream().map(item -> BriefingItemResponse.of(item, changes, grouper)).toList(),
                briefing.recommendedStartingPoint().map(item -> BriefingItemResponse.of(item, changes, grouper)).orElse(null));
    }
}
