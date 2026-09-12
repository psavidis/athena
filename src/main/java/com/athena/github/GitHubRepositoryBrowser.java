package com.athena.github;

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
     * Lists the repositories accessible to the authenticated account.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     */
    public List<Repository> listAccessibleRepositories() {
        return transport.fetchAccessibleRepositories(token);
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
