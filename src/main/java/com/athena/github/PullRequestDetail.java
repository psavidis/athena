package com.athena.github;

/**
 * A Pull Request's core metadata: title, author, and the base/head
 * revisions it spans.
 */
public record PullRequestDetail(int number, String title, String author, String baseRevision, String headRevision) {
}
