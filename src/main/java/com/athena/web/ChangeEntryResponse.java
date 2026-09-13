package com.athena.web;

import com.athena.semantic.ChangeCategory;
import com.athena.semantic.ReviewState;
import com.athena.semantic.TransformationKind;

/**
 * One Change Map entry (ticket #74). {@code id} is only the entry's position
 * in the response's {@code changes} list, stable within one response. To
 * reference this same Change again in a later request (detail view,
 * comments — ticket #75), use {@code changeKey}, an opaque encoding of its
 * {@link com.athena.semantic.ChangeIdentity}.
 *
 * <p>{@code category} is the coarse BEHAVIORAL/STRUCTURAL/MECHANICAL/UNKNOWN
 * classification review coverage is reported against; {@code kind} is the
 * specific detected transformation (rename, move, add, etc.) — several
 * distinct kinds can share one category, so a Change Map showing only
 * category tells the reviewer nothing beyond "structural" for most rows.
 */
public record ChangeEntryResponse(int id, String changeKey, String description, ChangeCategory category,
                                   TransformationKind kind, ReviewState reviewState, int occurrenceCount,
                                   int exceptionCount) {
}
