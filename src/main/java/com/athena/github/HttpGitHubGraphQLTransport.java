package com.athena.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Production {@link GitHubGraphQLTransport} backed by GitHub's real GraphQL
 * API (https://api.github.com/graphql), using the JDK's built-in HttpClient
 * and Jackson for JSON.
 */
public class HttpGitHubGraphQLTransport implements GitHubGraphQLTransport {

    private static final String GRAPHQL_ENDPOINT = "https://api.github.com/graphql";

    private final HttpClient httpClient;
    private final ObjectMapper json = new ObjectMapper();

    public HttpGitHubGraphQLTransport(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void resolveReviewThread(String token, String threadId) {
        String query = """
                mutation($threadId: ID!) {
                  resolveReviewThread(input: { threadId: $threadId }) {
                    thread { id isResolved }
                  }
                }
                """;
        ObjectMapper mapper = new ObjectMapper();
        var variables = mapper.createObjectNode().put("threadId", threadId);
        execute(token, query, variables, threadId);
    }

    @Override
    public void markFileAsViewed(String token, String repositoryFullName, int number, String path) {
        String pullRequestNodeId = fetchPullRequestNodeId(token, repositoryFullName, number);
        String mutation = """
                mutation($pullRequestId: ID!, $path: String!) {
                  markFileAsViewed(input: { pullRequestId: $pullRequestId, path: $path }) {
                    pullRequest { id }
                  }
                }
                """;
        ObjectMapper mapper = new ObjectMapper();
        var variables = mapper.createObjectNode()
                .put("pullRequestId", pullRequestNodeId)
                .put("path", path);
        execute(token, mutation, variables, repositoryFullName + "#" + number + " " + path);
    }

    private String fetchPullRequestNodeId(String token, String repositoryFullName, int number) {
        String[] parts = repositoryFullName.split("/", 2);
        String query = """
                query($owner: String!, $name: String!, $number: Int!) {
                  repository(owner: $owner, name: $name) {
                    pullRequest(number: $number) { id }
                  }
                }
                """;
        ObjectMapper mapper = new ObjectMapper();
        var variables = mapper.createObjectNode()
                .put("owner", parts[0])
                .put("name", parts[1])
                .put("number", number);
        JsonNode data = execute(token, query, variables, repositoryFullName + "#" + number);
        JsonNode idNode = data.path("repository").path("pullRequest").path("id");
        if (idNode.isMissingNode() || idNode.isNull()) {
            throw new GitHubResourceNotFoundException(
                    "Pull request " + number + " not found in " + repositoryFullName);
        }
        return idNode.asText();
    }

    private JsonNode execute(String token, String query, JsonNode variables, String resourceDescription) {
        var mapper = new ObjectMapper();
        var payload = mapper.createObjectNode();
        payload.put("query", query);
        payload.set("variables", variables);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GRAPHQL_ENDPOINT))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new GitHubAuthenticationException("Failed to reach GitHub: " + e.getMessage(), e);
        }

        if (response.statusCode() == 401) {
            throw new GitHubAuthenticationException("Bad credentials");
        }
        if (response.statusCode() != 200) {
            throw new GitHubAuthenticationException(
                    "GitHub GraphQL API returned unexpected status " + response.statusCode());
        }

        JsonNode body;
        try {
            body = json.readTree(response.body());
        } catch (IOException e) {
            throw new GitHubAuthenticationException("Could not parse GitHub GraphQL response: " + e.getMessage(), e);
        }

        if (body.has("errors") && body.path("errors").isArray() && !body.path("errors").isEmpty()) {
            String message = body.path("errors").get(0).path("message").asText("GraphQL error");
            throw new GitHubResourceNotFoundException(
                    "GitHub GraphQL error for " + resourceDescription + ": " + message);
        }

        return body.path("data");
    }
}
