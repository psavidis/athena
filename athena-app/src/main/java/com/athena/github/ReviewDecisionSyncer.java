package com.athena.github;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Synchronizes a reviewer's decision made in Athena — Approve or Request
 * Changes — to the real GitHub Pull Request as a native review (per §31's
 * projection table).
 *
 * <p>Idempotent per logical review decision (ticket #183): repeating a sync
 * call with identical arguments short-circuits instead of submitting a
 * second, duplicate review. A failed attempt is not remembered, so a
 * genuine retry after a failure still goes through.
 */
public class ReviewDecisionSyncer {

    private static final String APPROVE = "APPROVE";
    private static final String REQUEST_CHANGES = "REQUEST_CHANGES";

    private record ReviewKey(String repositoryFullName, int number, String event, String body) {
    }

    private final String token;
    private final GitHubTransport transport;
    private final Set<ReviewKey> syncedReviews = new HashSet<>();

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
        syncReview(repositoryFullName, number, APPROVE, body);
    }

    /**
     * Syncs a request-changes decision to GitHub.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the repository or Pull
     *         Request doesn't exist or isn't accessible
     */
    public void syncRequestChanges(String repositoryFullName, int number, String body) {
        syncReview(repositoryFullName, number, REQUEST_CHANGES, body);
    }

    private void syncReview(String repositoryFullName, int number, String event, String body) {
        ReviewKey key = new ReviewKey(repositoryFullName, number, event, body);
        if (syncedReviews.contains(key)) {
            return;
        }
        transport.postReview(token, repositoryFullName, number, event, body);
        syncedReviews.add(key);
    }
}
