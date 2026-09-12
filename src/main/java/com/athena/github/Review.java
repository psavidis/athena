package com.athena.github;

/**
 * A reviewer's current state on a Pull Request (e.g. APPROVED,
 * CHANGES_REQUESTED, COMMENTED, PENDING — GitHub's own review states).
 */
public record Review(String reviewer, String state) {
}
