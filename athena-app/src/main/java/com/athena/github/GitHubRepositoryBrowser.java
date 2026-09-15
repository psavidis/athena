package com.athena.github;

import com.athena.repository.ClosedPullRequestSummary;
import com.athena.repository.PullRequestSummary;
import com.athena.repository.Repository;

import java.time.Duration;
import java.net.http.HttpClient;
import java.util.List;

/**
 * Lets a user, once connected to GitHub (see {@link GitHubClient}), list
 * accessible repositories, list open Pull Requests on a repository, and
 * select a specific Pull Request for Athena to work with.
 */
public class GitHubRepositoryBrowser {

    private final String token;
    private final GitHubTransport transport;

    public GitHubRepositoryBrowser(String token) {
        this(token, new HttpGitHubTransport(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()));
    }

    public GitHubRepositoryBrowser(String token, GitHubTransport transport) {
        this.token = token;
        this.transport = transport;
    }

    /**
     * Lists the repositories accessible to the authenticated (user, e.g.
     * PAT) account.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     */
    public List<Repository> listAccessibleRepositories() {
        return transport.fetchAccessibleRepositories(token);
    }

    /**
     * Lists the repositories this token's GitHub App installation can
     * access. Use this instead of {@link #listAccessibleRepositories()}
     * when the held token is a GitHub App installation access token (see
     * {@link GitHubAppClient}) rather than a user/PAT token — GitHub
     * requires a different endpoint for each.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     */
    public List<Repository> listInstallationRepositories() {
        return transport.fetchInstallationRepositories(token);
    }

    /**
     * Lists the open Pull Requests for the given repository.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     */
    public List<PullRequestSummary> listOpenPullRequests(String repositoryFullName) {
        return transport.fetchOpenPullRequests(token, repositoryFullName);
    }

    /**
     * Lists the closed Pull Requests for the given repository, each flagged
     * as merged or closed without merging.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     */
    public List<ClosedPullRequestSummary> listClosedPullRequests(String repositoryFullName) {
        return transport.fetchClosedPullRequests(token, repositoryFullName);
    }

    /**
     * Selects a specific Pull Request by repository + number.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the Pull Request doesn't
     *         exist or isn't accessible
     */
    public PullRequestSummary selectPullRequest(String repositoryFullName, int number) {
        return transport.fetchPullRequest(token, repositoryFullName, number);
    }
}
