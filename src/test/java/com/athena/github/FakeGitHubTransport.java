package com.athena.github;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fake implementation of GitHubTransport standing in for real network calls
 * to api.github.com — the external system boundary tests should not cross
 * for real (Detroit-school exception).
 */
public class FakeGitHubTransport implements GitHubTransport {

    private final Map<String, String> tokenToUsername = new HashMap<>();
    private final Map<String, List<Repository>> tokenToRepositories = new HashMap<>();
    private final Map<String, List<PullRequestSummary>> repoToOpenPulls = new HashMap<>();

    public void acceptToken(String token, String username) {
        tokenToUsername.put(token, username);
    }

    public void rejectToken(String token) {
        tokenToUsername.remove(token);
    }

    public void addAccessibleRepository(String token, String fullName) {
        tokenToRepositories.computeIfAbsent(token, k -> new ArrayList<>())
                .add(new Repository(fullName));
    }

    public void addOpenPullRequest(String repositoryFullName, int number, String title) {
        repoToOpenPulls.computeIfAbsent(repositoryFullName, k -> new ArrayList<>())
                .add(new PullRequestSummary(number, title));
    }

    @Override
    public AuthenticatedUser fetchAuthenticatedUser(String token) {
        String username = tokenToUsername.get(token);
        if (username == null) {
            throw new GitHubAuthenticationException("Bad credentials");
        }
        return new AuthenticatedUser(username);
    }

    @Override
    public List<Repository> fetchAccessibleRepositories(String token) {
        if (!tokenToUsername.containsKey(token)) {
            throw new GitHubAuthenticationException("Bad credentials");
        }
        return tokenToRepositories.getOrDefault(token, List.of());
    }

    @Override
    public List<PullRequestSummary> fetchOpenPullRequests(String token, String repositoryFullName) {
        if (!tokenToUsername.containsKey(token)) {
            throw new GitHubAuthenticationException("Bad credentials");
        }
        return repoToOpenPulls.getOrDefault(repositoryFullName, List.of());
    }

    @Override
    public PullRequestSummary fetchPullRequest(String token, String repositoryFullName, int number) {
        if (!tokenToUsername.containsKey(token)) {
            throw new GitHubAuthenticationException("Bad credentials");
        }
        return repoToOpenPulls.getOrDefault(repositoryFullName, List.of()).stream()
                .filter(pr -> pr.number() == number)
                .findFirst()
                .orElseThrow(() -> new GitHubResourceNotFoundException(
                        "Pull request " + number + " not found in " + repositoryFullName));
    }
}
