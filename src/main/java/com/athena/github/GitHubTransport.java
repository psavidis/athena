package com.athena.github;

/**
 * The seam between {@link GitHubClient} and the actual GitHub API transport.
 * The default production implementation ({@link HttpGitHubTransport}) makes
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
}
