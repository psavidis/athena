package com.athena.github;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Synchronizes a reviewer's decision made in Athena — Approve or Request
 * Changes — to the real GitHub Pull Request as a native review (per §31's
 * projection table).
 */
public class ReviewDecisionSyncer {

    private static final String APPROVE = "APPROVE";
    private static final String REQUEST_CHANGES = "REQUEST_CHANGES";

    private final String token;
    private final GitHubTransport transport;

    public ReviewDecisionSyncer(String token) {
        this(token, new HttpGitHubTransport(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()));
    }

    public ReviewDecisionSyncer(String token, GitHubTransport transport) {
        this.token = token;
        this.transport = transport;
    }

    /**
     * Syncs an approval decision to GitHub.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the repository or Pull
     *         Request doesn't exist or isn't accessible
     */
    public void syncApproval(String repositoryFullName, int number, String body) {
        transport.postReview(token, repositoryFullName, number, APPROVE, body);
    }

    /**
     * Syncs a request-changes decision to GitHub.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the repository or Pull
     *         Request doesn't exist or isn't accessible
     */
    public void syncRequestChanges(String repositoryFullName, int number, String body) {
        transport.postReview(token, repositoryFullName, number, REQUEST_CHANGES, body);
    }
}
