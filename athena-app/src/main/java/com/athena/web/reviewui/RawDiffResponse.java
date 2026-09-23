package com.athena.web.reviewui;

/** The unified diff of one changed file between the two revisions (ticket #260). */
public record RawDiffResponse(String path, String diff) {
}
