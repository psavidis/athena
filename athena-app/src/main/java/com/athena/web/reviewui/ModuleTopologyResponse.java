package com.athena.web.reviewui;

import java.util.List;

/** The Semantic Canvas territory map for the selected PR (ticket #129). */
public record ModuleTopologyResponse(List<ModuleTerritoryResponse> territories, List<ModuleDependencyResponse> dependencies) {
}
