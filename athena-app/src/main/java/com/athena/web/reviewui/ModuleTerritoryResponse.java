package com.athena.web.reviewui;

import com.athena.semantic.ModuleStatus;
import com.athena.semantic.TechStack;

import java.util.List;

/** One module territory, serialized for the Semantic Canvas (ticket #129). */
public record ModuleTerritoryResponse(String moduleName, ModuleStatus status, int fileCount, String statusSummary,
                                       TechStack techStack, String techStackLabel, List<String> changeKeys) {
}
