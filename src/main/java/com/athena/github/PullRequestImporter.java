package com.athena.github;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Imports a Pull Request's core content — metadata, base/head revisions,
 * commits, changed files, and per-file textual diffs — as plain domain
 * objects, independent of GitHub's own API response shapes.
 */
public class PullRequestImporter {

    private final String token;
    private final GitHubTransport transport;

    public PullRequestImporter(String token) {
        this(token, new HttpGitHubTransport(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()));
    }

    public PullRequestImporter(String token, GitHubTransport transport) {
        this.token = token;
        this.transport = transport;
    }

    /**
     * Imports the given Pull Request's metadata, revisions, commits, and
     * changed files (with diffs where available).
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the repository or Pull
     *         Request doesn't exist or isn't accessible
     */
    public ImportedPullRequest importPullRequest(String repositoryFullName, int number) {
        PullRequestDetail detail = transport.fetchPullRequestDetail(token, repositoryFullName, number);
        return new ImportedPullRequest(
                detail.number(),
                detail.title(),
                detail.author(),
                detail.baseRevision(),
                detail.headRevision(),
                transport.fetchCommits(token, repositoryFullName, number),
                transport.fetchChangedFiles(token, repositoryFullName, number));
    }
}
