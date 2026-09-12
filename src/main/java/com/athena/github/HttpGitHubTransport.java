package com.athena.github;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production {@link GitHubTransport} backed by the real GitHub REST API
 * (https://api.github.com), using the JDK's built-in HttpClient.
 */
public class HttpGitHubTransport implements GitHubTransport {

    private static final String API_BASE = "https://api.github.com";

    // Minimal extraction of "login" from the JSON response body, avoiding a
    // full JSON library dependency for this one field. Revisit once more
    // endpoints need richer parsing (see #10/#11).
    private static final Pattern LOGIN_PATTERN = Pattern.compile("\"login\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient httpClient;

    public HttpGitHubTransport(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public AuthenticatedUser fetchAuthenticatedUser(String token) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/user"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
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
                    "GitHub returned unexpected status " + response.statusCode());
        }

        Matcher matcher = LOGIN_PATTERN.matcher(response.body());
        if (!matcher.find()) {
            throw new GitHubAuthenticationException("Could not parse authenticated user from GitHub response");
        }
        return new AuthenticatedUser(matcher.group(1));
    }
}
