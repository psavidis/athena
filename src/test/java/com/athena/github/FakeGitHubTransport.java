package com.athena.github;

import java.util.HashMap;
import java.util.Map;

/**
 * Fake implementation of GitHubTransport standing in for real network calls
 * to api.github.com — the external system boundary tests should not cross
 * for real (Detroit-school exception).
 */
public class FakeGitHubTransport implements GitHubTransport {

    private final Map<String, String> tokenToUsername = new HashMap<>();

    public void acceptToken(String token, String username) {
        tokenToUsername.put(token, username);
    }

    public void rejectToken(String token) {
        tokenToUsername.remove(token);
    }

    @Override
    public AuthenticatedUser fetchAuthenticatedUser(String token) {
        String username = tokenToUsername.get(token);
        if (username == null) {
            throw new GitHubAuthenticationException("Bad credentials");
        }
        return new AuthenticatedUser(username);
    }
}
