package com.athena.web.reviewui;

import com.athena.semantic.ModuleStatus;
import com.athena.semantic.TechStack;

import java.util.List;

/**
 * One module territory, serialized for the Semantic Canvas (ticket #129). {@code testChangeKeys}
 * is the subset of {@code changeKeys} whose Changes are in test code (ticket #285).
 */
public record ModuleTerritoryResponse(String moduleName, ModuleStatus status, int fileCount, String statusSummary,
                                       TechStack techStack, String techStackLabel, List<String> changeKeys,
                                       List<String> testChangeKeys) {
}
