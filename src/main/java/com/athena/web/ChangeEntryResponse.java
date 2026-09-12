package com.athena.web;

import com.athena.semantic.ChangeCategory;
import com.athena.semantic.ReviewState;

/**
 * One Change Map entry (ticket #74). {@code id} is only the entry's position
 * in the response's {@code changes} list, stable within one response. To
 * reference this same Change again in a later request (detail view,
 * comments — ticket #75), use {@code changeKey}, an opaque encoding of its
 * {@link com.athena.semantic.ChangeIdentity}.
 */
public record ChangeEntryResponse(int id, String changeKey, String description, ChangeCategory category,
                                   ReviewState reviewState, int occurrenceCount, int exceptionCount) {
}
