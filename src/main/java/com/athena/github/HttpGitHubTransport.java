package com.athena.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Production {@link GitHubTransport} backed by the real GitHub REST API
 * (https://api.github.com), using the JDK's built-in HttpClient and Jackson
 * for JSON parsing.
 */
public class HttpGitHubTransport implements GitHubTransport {

    private static final String API_BASE = "https://api.github.com";

    private final HttpClient httpClient;
    private final ObjectMapper json = new ObjectMapper();

    public HttpGitHubTransport(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public AuthenticatedUser fetchAuthenticatedUser(String token) {
        JsonNode body = get(token, "/user", null);
        return new AuthenticatedUser(body.path("login").asText());
    }

    @Override
    public List<Repository> fetchAccessibleRepositories(String token) {
        JsonNode body = get(token, "/user/repos", null);
        List<Repository> repositories = new ArrayList<>();
        for (JsonNode repo : body) {
            repositories.add(new Repository(repo.path("full_name").asText()));
        }
        return repositories;
    }

    @Override
    public List<Repository> fetchInstallationRepositories(String installationToken) {
        JsonNode body = get(installationToken, "/installation/repositories", null);
        List<Repository> repositories = new ArrayList<>();
        for (JsonNode repo : body.path("repositories")) {
            repositories.add(new Repository(repo.path("full_name").asText()));
        }
        return repositories;
    }

    @Override
    public List<PullRequestSummary> fetchOpenPullRequests(String token, String repositoryFullName) {
        JsonNode body = get(token, "/repos/" + repositoryFullName + "/pulls?state=open", repositoryFullName);
        List<PullRequestSummary> pullRequests = new ArrayList<>();
        for (JsonNode pr : body) {
            pullRequests.add(new PullRequestSummary(pr.path("number").asInt(), pr.path("title").asText()));
        }
        return pullRequests;
    }

    @Override
    public PullRequestSummary fetchPullRequest(String token, String repositoryFullName, int number) {
        JsonNode body = get(token, "/repos/" + repositoryFullName + "/pulls/" + number, repositoryFullName);
        return new PullRequestSummary(body.path("number").asInt(), body.path("title").asText());
    }

    @Override
    public PullRequestDetail fetchPullRequestDetail(String token, String repositoryFullName, int number) {
        JsonNode body = get(token, "/repos/" + repositoryFullName + "/pulls/" + number, repositoryFullName);
        return new PullRequestDetail(
                body.path("number").asInt(),
                body.path("title").asText(),
                body.path("user").path("login").asText(),
                body.path("base").path("sha").asText(),
                body.path("head").path("sha").asText());
    }

    @Override
    public List<Commit> fetchCommits(String token, String repositoryFullName, int number) {
        JsonNode body = get(token, "/repos/" + repositoryFullName + "/pulls/" + number + "/commits", repositoryFullName);
        List<Commit> commits = new ArrayList<>();
        for (JsonNode commit : body) {
            commits.add(new Commit(
                    commit.path("sha").asText(),
                    commit.path("commit").path("message").asText()));
        }
        return commits;
    }

    @Override
    public List<ChangedFile> fetchChangedFiles(String token, String repositoryFullName, int number) {
        JsonNode body = get(token, "/repos/" + repositoryFullName + "/pulls/" + number + "/files", repositoryFullName);
        List<ChangedFile> files = new ArrayList<>();
        for (JsonNode file : body) {
            JsonNode patchNode = file.path("patch");
            Optional<String> diff = patchNode.isMissingNode() ? Optional.empty() : Optional.of(patchNode.asText());
            files.add(new ChangedFile(file.path("filename").asText(), file.path("status").asText(), diff));
        }
        return files;
    }

    @Override
    public List<ReviewComment> fetchReviewComments(String token, String repositoryFullName, int number) {
        JsonNode body = get(token, "/repos/" + repositoryFullName + "/pulls/" + number + "/comments", repositoryFullName);
        List<ReviewComment> comments = new ArrayList<>();
        for (JsonNode comment : body) {
            comments.add(new ReviewComment(
                    comment.path("user").path("login").asText(),
                    comment.path("body").asText(),
                    comment.path("path").asText()));
        }
        return comments;
    }

    @Override
    public List<Review> fetchReviews(String token, String repositoryFullName, int number) {
        JsonNode body = get(token, "/repos/" + repositoryFullName + "/pulls/" + number + "/reviews", repositoryFullName);
        List<Review> reviews = new ArrayList<>();
        for (JsonNode review : body) {
            reviews.add(new Review(
                    review.path("user").path("login").asText(),
                    review.path("state").asText()));
        }
        return reviews;
    }

    @Override
    public String fetchRepositoryPermission(String token, String repositoryFullName) {
        AuthenticatedUser user = fetchAuthenticatedUser(token);
        JsonNode body = get(token,
                "/repos/" + repositoryFullName + "/collaborators/" + user.username() + "/permission",
                repositoryFullName);
        return body.path("permission").asText();
    }

    @Override
    public void postGeneralComment(String token, String repositoryFullName, int number, String body) {
        ObjectMapper mapper = new ObjectMapper();
        var payload = mapper.createObjectNode().put("body", body);
        post(token, "/repos/" + repositoryFullName + "/issues/" + number + "/comments", payload.toString(),
                repositoryFullName + "#" + number);
    }

    @Override
    public void postLineComment(String token, String repositoryFullName, int number, String body, String path,
                                 int line) {
        PullRequestDetail detail = fetchPullRequestDetail(token, repositoryFullName, number);
        ObjectMapper mapper = new ObjectMapper();
        var payload = mapper.createObjectNode()
                .put("body", body)
                .put("commit_id", detail.headRevision())
                .put("path", path)
                .put("line", line)
                .put("side", "RIGHT");
        post(token, "/repos/" + repositoryFullName + "/pulls/" + number + "/comments", payload.toString(),
                repositoryFullName + "#" + number);
    }

    @Override
    public void postReview(String token, String repositoryFullName, int number, String event, String body) {
        ObjectMapper mapper = new ObjectMapper();
        var payload = mapper.createObjectNode()
                .put("body", body)
                .put("event", event);
        post(token, "/repos/" + repositoryFullName + "/pulls/" + number + "/reviews", payload.toString(),
                repositoryFullName + "#" + number);
    }

    private JsonNode get(String token, String path, String resourceDescriptionForNotFound) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + path))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

        HttpResponse<String> response = send(request);
        return parseBody(response, resourceDescriptionForNotFound, path);
    }

    private void post(String token, String path, String jsonBody, String resourceDescriptionForNotFound) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + path))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = send(request);
        parseBody(response, resourceDescriptionForNotFound, path);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new GitHubAuthenticationException("Failed to reach GitHub: " + e.getMessage(), e);
        }
    }

    private JsonNode parseBody(HttpResponse<String> response, String resourceDescriptionForNotFound, String path) {
        if (response.statusCode() == 401) {
            throw new GitHubAuthenticationException("Bad credentials");
        }
        if (response.statusCode() == 404) {
            throw new GitHubResourceNotFoundException(
                    resourceDescriptionForNotFound != null
                            ? "Not found: " + resourceDescriptionForNotFound
                            : "Not found: " + path);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GitHubAuthenticationException(
                    "GitHub returned unexpected status " + response.statusCode());
        }

        if (response.body() == null || response.body().isBlank()) {
            return json.nullNode();
        }
        try {
            return json.readTree(response.body());
        } catch (IOException e) {
            throw new GitHubAuthenticationException("Could not parse GitHub response: " + e.getMessage(), e);
        }
    }
}
