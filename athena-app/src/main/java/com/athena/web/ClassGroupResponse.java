package com.athena.web;

import java.util.List;

/**
 * One collapsible Change Map group: every {@link ChangeEntryResponse} that
 * shares an enclosing type, serialized for the frontend (epic #5 §14
 * follow-up — see {@link com.athena.reviewui.ClassGroup}).
 */
public record ClassGroupResponse(String enclosingType, List<ChangeEntryResponse> entries) {
}
