package com.athena.repository;

/**
 * A repository accessible through some {@link RepositoryProvider}, identified
 * by its "owner/name" full name (e.g. "octocat/Hello-World"). Provider-
 * independent — GitHub is the current provider, but this shape carries no
 * GitHub-specific concept.
 */
public record Repository(String fullName) {
}
