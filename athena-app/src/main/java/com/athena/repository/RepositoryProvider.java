package com.athena.repository;

import java.util.List;

/**
 * The contract every repository-hosting integration implements (ticket
 * #113/#144) — repository listing, Pull Request discovery, and importing a
 * specific Pull Request's content. GitHub is the current implementation
 * ({@code com.athena.github}'s {@code GitHubRepositoryProvider}); a future
 * provider (GitLab, etc.) is an implementation of this same interface, not a
 * redesign of the callers listed below.
 *
 * <p>Athena's main repository-browsing and PR-discovery code (the web
 * controllers under {@code com.athena.web.github}) depends on this interface
 * rather than on any provider's own classes.
 */
public interface RepositoryProvider {

    /** Every repository this provider currently has access to. */
    List<Repository> accessibleRepositories();

    /** The open Pull Requests on the given repository (its "owner/name" full name). */
    List<PullRequestSummary> openPullRequests(String repositoryFullName);

    /**
     * Imports a specific Pull Request's metadata, revisions, commits, and
     * changed files (with diffs where available).
     */
    ImportedPullRequest importPullRequest(String repositoryFullName, int number);
}
