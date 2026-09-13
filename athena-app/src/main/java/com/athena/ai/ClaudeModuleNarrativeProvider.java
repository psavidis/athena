package com.athena.ai;

import com.athena.semantic.ModuleGroup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Production {@link ModuleNarrativeProvider} backed directly by the
 * Anthropic Messages API — the module-narrative counterpart to
 * {@link ClaudeAiProvider}, kept separate because it answers a different
 * question (why a module was touched, not what a reviewer may have missed)
 * against a much smaller, structurally-derived prompt.
 */
public class ClaudeModuleNarrativeProvider implements ModuleNarrativeProvider {

    private static final URI API_BASE = URI.create("https://api.anthropic.com/v1/messages");
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String MODEL = "claude-sonnet-5";

    private final HttpClient httpClient;
    private final String apiKey;
    private final ObjectMapper json = new ObjectMapper();

    public ClaudeModuleNarrativeProvider(HttpClient httpClient, String apiKey) {
        this.httpClient = httpClient;
        this.apiKey = apiKey;
    }

    @Override
    public String explain(ModuleGroup moduleGroup) {
        JsonNode responseBody = send(ModuleNarrativePrompt.build(moduleGroup));
        return responseBody.path("content").path(0).path("text").asText("").strip();
    }

    private JsonNode send(String prompt) {
        ObjectNode payload = json.createObjectNode();
        payload.put("model", MODEL);
        payload.put("max_tokens", 256);
        ArrayNode messages = payload.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(API_BASE)
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
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
            throw new AiProviderException("Failed to reach Claude: " + e.getMessage(), e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AiProviderException("Claude returned unexpected status " + response.statusCode());
        }
        try {
            return json.readTree(response.body());
        } catch (IOException e) {
            throw new AiProviderException("Could not parse Claude's response: " + e.getMessage(), e);
        }
    }
}
