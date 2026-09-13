package com.athena.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Production {@link GitHubAppTransport} backed by the real GitHub REST API.
 */
class HttpGitHubAppTransport implements GitHubAppTransport {

    private static final String API_BASE = "https://api.github.com";

    private final HttpClient httpClient;
    private final ObjectMapper json = new ObjectMapper();

    HttpGitHubAppTransport(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public GitHubAppRegistration convertManifest(String code) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/app-manifests/" + code + "/conversions"))
                .header("Accept", "application/vnd.github+json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        JsonNode body = parseBody(send(request));
        return new GitHubAppRegistration(
                body.path("id").asText(),
                body.path("slug").asText(),
                body.path("pem").asText(),
                body.path("webhook_secret").asText());
    }

    @Override
    public String createInstallationAccessToken(String appJwt, long installationId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/app/installations/" + installationId + "/access_tokens"))
                .header("Authorization", "Bearer " + appJwt)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        JsonNode body = parseBody(send(request));
        return body.path("token").asText();
    }

    @Override
    public String fetchInstallationAccountLogin(String appJwt, long installationId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/app/installations/" + installationId))
                .header("Authorization", "Bearer " + appJwt)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

        JsonNode body = parseBody(send(request));
        return body.path("account").path("login").asText();
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

    private JsonNode parseBody(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GitHubAuthenticationException(
                    "GitHub App request failed with status " + response.statusCode() + ": " + response.body());
        }
        try {
            return json.readTree(response.body());
        } catch (IOException e) {
            throw new GitHubAuthenticationException("Could not parse GitHub response: " + e.getMessage(), e);
        }
    }
}
