package com.athena.web.reviewreplay;

/**
 * Request body to promote one {@code ReviewOutcome} item into project
 * memory (ticket #216): which section it's in, the source moment's
 * reference identifying it within that section, and the developer's own
 * words for the fact being recorded.
 */
public record PromoteOutcomeItemRequest(String section, String reference, String fact) {
}
