package com.athena.web.reviewui;

import java.util.List;

/**
 * A Change's full Semantic Profile (ticket #94): one {@link SemanticDimensionEntryResponse}
 * per classification the Change carries. A dimension with no classification simply
 * contributes no entry — never a null/placeholder one, mirroring {@code SemanticProfile}'s
 * own "absent means unclassified" rule.
 */
public record SemanticProfileResponse(List<SemanticDimensionEntryResponse> dimensions) {
}
