package com.athena.repository;

/**
 * A lightweight summary of a closed Pull Request — enough to list one and
 * know whether it was merged or closed without merging, before importing
 * its full content. Provider-independent.
 */
public record ClosedPullRequestSummary(int number, String title, boolean merged) {
}
