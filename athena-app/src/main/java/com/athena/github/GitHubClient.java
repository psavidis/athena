package com.athena.github;

import java.time.Duration;
import java.net.http.HttpClient;

/**
 * Authenticates against GitHub using a Personal Access Token (PAT) and
 * exposes the identity of the authenticated account. This is the MVP
 * authentication mechanism for Athena's GitHub Integration epic (#3) — see
 * that epic's Open Questions for why PAT was chosen over OAuth App/GitHub
 * App for the first pass.
 */
public class GitHubClient {

    private final String token;
    private final GitHubTransport transport;

    /**
     * Production constructor: talks to the real GitHub REST API over HTTPS.
     */
    public GitHubClient(String token) {
        this(token, new HttpGitHubTransport(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()));
    }

    /**
     * Test/advanced constructor: substitute any {@link GitHubTransport}
     * (e.g. a fake, to avoid crossing the network boundary in tests).
     */
    public GitHubClient(String token, GitHubTransport transport) {
        this.token = token;
        this.transport = transport;
    }

    /**
     * Attempts to authenticate with GitHub using this client's token and
     * identify the authenticated account. Never throws — failures are
     * reported via the returned {@link GitHubConnectionResult}.
     */
    public GitHubConnectionResult connect() {
        try {
            AuthenticatedUser user = transport.fetchAuthenticatedUser(token);
            return GitHubConnectionResult.success(user.username());
        } catch (GitHubAuthenticationException e) {
            return GitHubConnectionResult.failure(e.getMessage());
        }
    }
}
