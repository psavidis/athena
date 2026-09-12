package com.athena.web;

import com.athena.semantic.ChangeCategory;
import com.athena.semantic.ReviewState;

/** One Change Map entry (ticket #74), keyed by its position in the response's {@code changes} list. */
public record ChangeEntryResponse(int id, String description, ChangeCategory category, ReviewState reviewState,
                                   int occurrenceCount, int exceptionCount) {
}
