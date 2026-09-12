package com.athena.github;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Imports a Pull Request's existing review comments and review state, and
 * the authenticated user's permission level on a repository.
 */
public class ReviewDataImporter {

    private final String token;
    private final GitHubTransport transport;

    public ReviewDataImporter(String token) {
        this(token, new HttpGitHubTransport(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()));
    }

    public ReviewDataImporter(String token, GitHubTransport transport) {
        this.token = token;
        this.transport = transport;
    }

    /**
     * Imports the given Pull Request's existing review comments and review
     * state.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the repository or Pull
     *         Request doesn't exist or isn't accessible
     */
    public ImportedReviewData importReviewData(String repositoryFullName, int number) {
        return new ImportedReviewData(
                transport.fetchReviewComments(token, repositoryFullName, number),
                transport.fetchReviews(token, repositoryFullName, number));
    }

    /**
     * Imports the authenticated user's permission level on the given
     * repository (e.g. "read", "write", "admin").
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the repository doesn't
     *         exist or isn't accessible
     */
    public String importPermission(String repositoryFullName) {
        return transport.fetchRepositoryPermission(token, repositoryFullName);
    }
}
