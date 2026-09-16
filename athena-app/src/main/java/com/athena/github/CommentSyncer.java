package com.athena.github;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Synchronizes comments written in Athena to the real GitHub Pull Request:
 * general (PR-scoped) comments and line-scoped review comments (per §31's
 * projection table).
 *
 * <p>Idempotent per logical comment (ticket #183): repeating a sync call
 * with identical arguments (a double-click, a network retry) short-circuits
 * instead of posting a second, duplicate comment. A failed attempt is not
 * remembered, so a genuine retry after a failure still goes through.
 */
public class CommentSyncer {

    private record GeneralCommentKey(String repositoryFullName, int number, String body) {
    }

    private record LineCommentKey(String repositoryFullName, int number, String body, String path, int line) {
    }

    private final String token;
    private final GitHubTransport transport;
    private final Set<GeneralCommentKey> syncedGeneralComments = new HashSet<>();
    private final Set<LineCommentKey> syncedLineComments = new HashSet<>();

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
        GeneralCommentKey key = new GeneralCommentKey(repositoryFullName, number, body);
        if (syncedGeneralComments.contains(key)) {
            return;
        }
        transport.postGeneralComment(token, repositoryFullName, number, body);
        syncedGeneralComments.add(key);
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
        LineCommentKey key = new LineCommentKey(repositoryFullName, number, body, path, line);
        if (syncedLineComments.contains(key)) {
            return;
        }
        transport.postLineComment(token, repositoryFullName, number, body, path, line);
        syncedLineComments.add(key);
    }
}
