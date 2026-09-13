package com.athena.github;

/**
 * The seam between {@link DiscussionAndViewedFileSyncer} and GitHub's
 * GraphQL API (https://api.github.com/graphql). Separate from
 * {@link GitHubTransport} (REST) because resolving a review thread and
 * marking a file as viewed have no REST equivalent — GitHub only exposes
 * them via GraphQL mutations (`resolveReviewThread`, `markFileAsViewed`).
 */
public interface GitHubGraphQLTransport {

    /**
     * Resolves a review discussion thread.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the thread doesn't exist or
     *         can't be resolved
     */
    void resolveReviewThread(String token, String threadId);

    /**
     * Marks a file as viewed on a Pull Request.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the repository, Pull
     *         Request, or file doesn't exist or isn't accessible
     */
    void markFileAsViewed(String token, String repositoryFullName, int number, String path);
}
