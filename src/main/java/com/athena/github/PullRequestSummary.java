package com.athena.github;

/**
 * A lightweight summary of a Pull Request — enough to list and select one,
 * before importing its full content (see the "Import PR metadata..." ticket).
 */
public record PullRequestSummary(int number, String title) {
}
