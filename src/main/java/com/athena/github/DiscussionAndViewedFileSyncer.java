package com.athena.github;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Synchronizes resolved discussions and viewed-file state to GitHub via its
 * GraphQL API — the only place GitHub exposes these two operations (REST
 * has no equivalent for either). "Where supported" per §31's projection
 * table and this epic's Scope Boundary: both operations are implemented
 * here since GraphQL does support them, even though REST doesn't.
 */
public class DiscussionAndViewedFileSyncer {

    private final String token;
    private final GitHubGraphQLTransport transport;

    public DiscussionAndViewedFileSyncer(String token) {
        this(token, new HttpGitHubGraphQLTransport(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()));
    }

    public DiscussionAndViewedFileSyncer(String token, GitHubGraphQLTransport transport) {
        this.token = token;
        this.transport = transport;
    }

    /**
     * Resolves a review discussion thread on GitHub.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the thread doesn't exist or
     *         can't be resolved
     */
    public void resolveDiscussionThread(String threadId) {
        transport.resolveReviewThread(token, threadId);
    }

    /**
     * Marks a file as viewed on a GitHub Pull Request.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the repository, Pull
     *         Request, or file doesn't exist or isn't accessible
     */
    public void markFileAsViewed(String repositoryFullName, int number, String path) {
        transport.markFileAsViewed(token, repositoryFullName, number, path);
    }
}
