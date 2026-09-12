package com.athena.github;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fake implementation of GitHubTransport standing in for real network calls
 * to api.github.com — the external system boundary tests should not cross
 * for real (Detroit-school exception).
 */
public class FakeGitHubTransport implements GitHubTransport {

    private record Key(String repo, int number) {
    }

    private final Map<String, String> tokenToUsername = new HashMap<>();
    private final Map<String, List<Repository>> tokenToRepositories = new HashMap<>();
    private final Map<String, List<PullRequestSummary>> repoToOpenPulls = new HashMap<>();
    private final Map<Key, PullRequestDetail> pullRequestDetails = new HashMap<>();
    private final Map<Key, List<Commit>> pullRequestCommits = new HashMap<>();
    private final Map<Key, List<ChangedFile>> pullRequestChangedFiles = new HashMap<>();
    private final Map<Key, List<ReviewComment>> pullRequestReviewComments = new HashMap<>();
    private final Map<Key, List<Review>> pullRequestReviews = new HashMap<>();
    private final Map<String, String> repositoryPermissions = new HashMap<>();

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

    public void addPullRequestDetail(String repositoryFullName, int number, String title, String author,
                                      String baseRevision, String headRevision) {
        pullRequestDetails.put(new Key(repositoryFullName, number),
                new PullRequestDetail(number, title, author, baseRevision, headRevision));
    }

    public void addCommit(String repositoryFullName, int number, String sha, String message) {
        pullRequestCommits.computeIfAbsent(new Key(repositoryFullName, number), k -> new ArrayList<>())
                .add(new Commit(sha, message));
    }

    public void addChangedFile(String repositoryFullName, int number, String path, String status, String diff) {
        pullRequestChangedFiles.computeIfAbsent(new Key(repositoryFullName, number), k -> new ArrayList<>())
                .add(new ChangedFile(path, status, Optional.ofNullable(diff)));
    }

    public void addReviewComment(String repositoryFullName, int number, String author, String body, String path) {
        pullRequestReviewComments.computeIfAbsent(new Key(repositoryFullName, number), k -> new ArrayList<>())
                .add(new ReviewComment(author, body, path));
    }

    public void addReview(String repositoryFullName, int number, String reviewer, String state) {
        pullRequestReviews.computeIfAbsent(new Key(repositoryFullName, number), k -> new ArrayList<>())
                .add(new Review(reviewer, state));
    }

    public void setPermission(String repositoryFullName, String level) {
        repositoryPermissions.put(repositoryFullName, level);
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
        requireValidToken(token);
        return tokenToRepositories.getOrDefault(token, List.of());
    }

    @Override
    public List<PullRequestSummary> fetchOpenPullRequests(String token, String repositoryFullName) {
        requireValidToken(token);
        return repoToOpenPulls.getOrDefault(repositoryFullName, List.of());
    }

    @Override
    public PullRequestSummary fetchPullRequest(String token, String repositoryFullName, int number) {
        requireValidToken(token);
        return repoToOpenPulls.getOrDefault(repositoryFullName, List.of()).stream()
                .filter(pr -> pr.number() == number)
                .findFirst()
                .orElseThrow(() -> new GitHubResourceNotFoundException(
                        "Pull request " + number + " not found in " + repositoryFullName));
    }

    @Override
    public PullRequestDetail fetchPullRequestDetail(String token, String repositoryFullName, int number) {
        requireValidToken(token);
        PullRequestDetail detail = pullRequestDetails.get(new Key(repositoryFullName, number));
        if (detail == null) {
            throw new GitHubResourceNotFoundException(
                    "Pull request " + number + " not found in " + repositoryFullName);
        }
        return detail;
    }

    @Override
    public List<Commit> fetchCommits(String token, String repositoryFullName, int number) {
        requireValidToken(token);
        return pullRequestCommits.getOrDefault(new Key(repositoryFullName, number), List.of());
    }

    @Override
    public List<ChangedFile> fetchChangedFiles(String token, String repositoryFullName, int number) {
        requireValidToken(token);
        return pullRequestChangedFiles.getOrDefault(new Key(repositoryFullName, number), List.of());
    }

    @Override
    public List<ReviewComment> fetchReviewComments(String token, String repositoryFullName, int number) {
        requireValidToken(token);
        return pullRequestReviewComments.getOrDefault(new Key(repositoryFullName, number), List.of());
    }

    @Override
    public List<Review> fetchReviews(String token, String repositoryFullName, int number) {
        requireValidToken(token);
        return pullRequestReviews.getOrDefault(new Key(repositoryFullName, number), List.of());
    }

    @Override
    public String fetchRepositoryPermission(String token, String repositoryFullName) {
        requireValidToken(token);
        String level = repositoryPermissions.get(repositoryFullName);
        if (level == null) {
            throw new GitHubResourceNotFoundException("No permission recorded for " + repositoryFullName);
        }
        return level;
    }

    private void requireValidToken(String token) {
        if (!tokenToUsername.containsKey(token)) {
            throw new GitHubAuthenticationException("Bad credentials");
        }
    }
}
