package com.athena.github;

import java.util.List;

/**
 * The seam between GitHub-facing clients (e.g. {@link GitHubClient},
 * {@link GitHubRepositoryBrowser}) and the actual GitHub API transport. The
 * default production implementation ({@link HttpGitHubTransport}) makes
 * real HTTP calls to the GitHub REST API; tests substitute a fake so they
 * don't cross the network boundary.
 */
public interface GitHubTransport {

    /**
     * Fetches the account identified by the given Personal Access Token.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     */
    AuthenticatedUser fetchAuthenticatedUser(String token);

    /**
     * Fetches the repositories accessible to the account identified by the
     * given token.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     */
    List<Repository> fetchAccessibleRepositories(String token);

    /**
     * Fetches the open Pull Requests for the given repository.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the repository doesn't
     *         exist or isn't accessible
     */
    List<PullRequestSummary> fetchOpenPullRequests(String token, String repositoryFullName);

    /**
     * Fetches a specific Pull Request by number.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the repository or Pull
     *         Request doesn't exist or isn't accessible
     */
    PullRequestSummary fetchPullRequest(String token, String repositoryFullName, int number);

    /**
     * Fetches a Pull Request's core metadata (title, author, base/head
     * revisions).
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the repository or Pull
     *         Request doesn't exist or isn't accessible
     */
    PullRequestDetail fetchPullRequestDetail(String token, String repositoryFullName, int number);

    /**
     * Fetches a Pull Request's commits.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the repository or Pull
     *         Request doesn't exist or isn't accessible
     */
    List<Commit> fetchCommits(String token, String repositoryFullName, int number);

    /**
     * Fetches a Pull Request's changed files, including each file's status
     * and textual diff (when GitHub provides one).
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the repository or Pull
     *         Request doesn't exist or isn't accessible
     */
    List<ChangedFile> fetchChangedFiles(String token, String repositoryFullName, int number);

    /**
     * Fetches a Pull Request's existing review comments.
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the repository or Pull
     *         Request doesn't exist or isn't accessible
     */
    List<ReviewComment> fetchReviewComments(String token, String repositoryFullName, int number);

    /**
     * Fetches a Pull Request's reviews (per-reviewer state).
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the repository or Pull
     *         Request doesn't exist or isn't accessible
     */
    List<Review> fetchReviews(String token, String repositoryFullName, int number);

    /**
     * Fetches the authenticated user's permission level on a repository
     * (e.g. "read", "write", "admin").
     *
     * @throws GitHubAuthenticationException if the token is invalid/rejected
     * @throws GitHubResourceNotFoundException if the repository doesn't
     *         exist or isn't accessible
     */
    String fetchRepositoryPermission(String token, String repositoryFullName);
}
