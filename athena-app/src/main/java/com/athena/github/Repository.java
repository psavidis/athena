package com.athena.github;

/**
 * A GitHub repository accessible to the authenticated account, identified by
 * its "owner/name" full name (e.g. "octocat/Hello-World").
 */
public record Repository(String fullName) {
}
