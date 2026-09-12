package com.athena.github;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Synchronizes comments written in Athena to the real GitHub Pull Request:
 * general (PR-scoped) comments and line-scoped review comments (per §31's
 * projection table).
 */
public class CommentSyncer {

    private final String token;
    private final GitHubTransport transport;

    public CommentSyncer(String token) {
        this(token, new HttpGitHubTransport(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()));
    }

    public CommentSyncer(String token, GitHubTransport transport) {
        this.token = token;
        this.transport = transport;
    }

    /**
     * Syncs a general (PR-scoped) comment to GitHub.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the repository or Pull
     *         Request doesn't exist or isn't accessible
     */
    public void syncGeneralComment(String repositoryFullName, int number, String body) {
        transport.postGeneralComment(token, repositoryFullName, number, body);
    }

    /**
     * Syncs a line-scoped review comment to GitHub, anchored to the Pull
     * Request's current head revision.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the repository or Pull
     *         Request doesn't exist or isn't accessible
     */
    public void syncLineComment(String repositoryFullName, int number, String body, String path, int line) {
        transport.postLineComment(token, repositoryFullName, number, body, path, line);
    }
}
