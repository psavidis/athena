package com.athena.web.reviewui;

/** One changed file no Change represents (ticket #260). {@code reasonLabel} is the reviewer-facing wording. */
public record UnrepresentedFileResponse(String path, String status, int linesChanged, int hunkCount, String reason,
                                        String reasonLabel) {
}
