package com.athena.web.reviewui;

import com.athena.semantic.ChangeCategory;

import java.util.List;
import java.util.Map;

/** The Change Map + PR Understanding View, serialized for the frontend (ticket #74). */
public record ChangeMapResponse(String prTitle, Map<ChangeCategory, Integer> categoryCounts,
                                 List<ChangeEntryResponse> changes, List<ClassGroupResponse> classGroups) {
}
