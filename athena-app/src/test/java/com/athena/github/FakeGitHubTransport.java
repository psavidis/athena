package com.athena.github;

import com.athena.repository.ChangedFile;
import com.athena.repository.ClosedPullRequestSummary;
import com.athena.repository.Commit;
import com.athena.repository.PullRequestSummary;
import com.athena.repository.Repository;

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
    private final Map<String, List<ClosedPullRequestSummary>> repoToClosedPulls = new HashMap<>();
    private final Map<Key, PullRequestDetail> pullRequestDetails = new HashMap<>();
    private final Map<Key, List<Commit>> pullRequestCommits = new HashMap<>();
    private final Map<Key, List<ChangedFile>> pullRequestChangedFiles = new HashMap<>();
    private final Map<Key, List<ReviewComment>> pullRequestReviewComments = new HashMap<>();
    private final Map<Key, List<Review>> pullRequestReviews = new HashMap<>();
    private final Map<Key, String> pullRequestBodies = new HashMap<>();
    private final Map<String, String> repositoryPermissions = new HashMap<>();
    private final Map<Key, Boolean> commentSyncEnabled = new HashMap<>();
    private final Map<Key, List<String>> postedGeneralComments = new HashMap<>();
    private final Map<Key, List<PostedLineComment>> postedLineComments = new HashMap<>();
    private final Map<Key, Boolean> reviewSyncEnabled = new HashMap<>();
    private final Map<Key, List<PostedReview>> postedReviews = new HashMap<>();

    public record PostedLineComment(String body, String path, int line) {
    }

    public record PostedReview(String event, String body) {
    }

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

    public void addClosedPullRequest(String repositoryFullName, int number, String title, boolean merged) {
        repoToClosedPulls.computeIfAbsent(repositoryFullName, k -> new ArrayList<>())
                .add(new ClosedPullRequestSummary(number, title, merged));
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

    public void setPullRequestBody(String repositoryFullName, int number, String body) {
        pullRequestBodies.put(new Key(repositoryFullName, number), body);
    }

    public void setPermission(String repositoryFullName, String level) {
        repositoryPermissions.put(repositoryFullName, level);
    }

    public void enableCommentSync(String repositoryFullName, int number) {
        commentSyncEnabled.put(new Key(repositoryFullName, number), true);
    }

    public List<String> postedGeneralComments(String repositoryFullName, int number) {
        return postedGeneralComments.getOrDefault(new Key(repositoryFullName, number), List.of());
    }

    public List<PostedLineComment> postedLineComments(String repositoryFullName, int number) {
        return postedLineComments.getOrDefault(new Key(repositoryFullName, number), List.of());
    }

    public void enableReviewSync(String repositoryFullName, int number) {
        reviewSyncEnabled.put(new Key(repositoryFullName, number), true);
    }

    public List<PostedReview> postedReviews(String repositoryFullName, int number) {
        return postedReviews.getOrDefault(new Key(repositoryFullName, number), List.of());
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
    public List<Repository> fetchInstallationRepositories(String installationToken) {
        requireValidToken(installationToken);
        return tokenToRepositories.getOrDefault(installationToken, List.of());
    }

    @Override
    public List<PullRequestSummary> fetchOpenPullRequests(String token, String repositoryFullName) {
        requireValidToken(token);
        return repoToOpenPulls.getOrDefault(repositoryFullName, List.of());
    }

    @Override
    public List<ClosedPullRequestSummary> fetchClosedPullRequests(String token, String repositoryFullName) {
        requireValidToken(token);
        return repoToClosedPulls.getOrDefault(repositoryFullName, List.of());
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
    public String fetchPullRequestBody(String token, String repositoryFullName, int number) {
        requireValidToken(token);
        return pullRequestBodies.getOrDefault(new Key(repositoryFullName, number), "");
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

    @Override
    public void postGeneralComment(String token, String repositoryFullName, int number, String body) {
        requireValidToken(token);
        requireSyncEnabled(repositoryFullName, number);
        postedGeneralComments.computeIfAbsent(new Key(repositoryFullName, number), k -> new ArrayList<>())
                .add(body);
    }

    @Override
    public void postLineComment(String token, String repositoryFullName, int number, String body, String path,
                                 int line) {
        requireValidToken(token);
        requireSyncEnabled(repositoryFullName, number);
        postedLineComments.computeIfAbsent(new Key(repositoryFullName, number), k -> new ArrayList<>())
                .add(new PostedLineComment(body, path, line));
    }

    @Override
    public void postReview(String token, String repositoryFullName, int number, String event, String body) {
        requireValidToken(token);
        if (!reviewSyncEnabled.getOrDefault(new Key(repositoryFullName, number), false)) {
            throw new GitHubResourceNotFoundException(
                    "Pull request " + number + " not found in " + repositoryFullName);
        }
        postedReviews.computeIfAbsent(new Key(repositoryFullName, number), k -> new ArrayList<>())
                .add(new PostedReview(event, body));
    }

    private void requireSyncEnabled(String repositoryFullName, int number) {
        if (!commentSyncEnabled.getOrDefault(new Key(repositoryFullName, number), false)) {
            throw new GitHubResourceNotFoundException(
                    "Pull request " + number + " not found in " + repositoryFullName);
        }
    }

    private void requireValidToken(String token) {
        if (!tokenToUsername.containsKey(token)) {
            throw new GitHubAuthenticationException("Bad credentials");
        }
    }
}
