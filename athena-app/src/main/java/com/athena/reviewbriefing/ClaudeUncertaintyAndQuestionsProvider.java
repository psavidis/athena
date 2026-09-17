package com.athena.reviewbriefing;

import com.athena.ai.AiProviderException;
import com.athena.semantic.Change;
import com.athena.semantic.SemanticProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Production {@link UncertaintyAndQuestionsProvider} backed directly by
 * the Anthropic Messages API — mirrors {@link
 * ClaudeSemanticChangeSummaryProvider}'s (#219) own structure, but parses
 * Claude's response as the structured JSON {@link
 * UncertaintyAndQuestionsPrompt} asks for instead of taking it as free
 * text.
 */
public class ClaudeUncertaintyAndQuestionsProvider implements UncertaintyAndQuestionsProvider {

    private static final URI API_BASE = URI.create("https://api.anthropic.com/v1/messages");
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String MODEL = "claude-sonnet-5";

    private final HttpClient httpClient;
    private final String apiKey;
    private final ObjectMapper json = new ObjectMapper();

    public ClaudeUncertaintyAndQuestionsProvider(HttpClient httpClient, String apiKey) {
        this.httpClient = httpClient;
        this.apiKey = apiKey;
    }

    @Override
    public UncertaintyAndQuestions analyze(List<Change> changes, List<SemanticProfile> profiles) {
        JsonNode responseBody = send(UncertaintyAndQuestionsPrompt.build(changes, profiles));
        String text = responseBody.path("content").path(0).path("text").asText("").strip();
        return parse(text);
    }

    private UncertaintyAndQuestions parse(String text) {
        JsonNode root;
        try {
            root = json.readTree(text);
        } catch (IOException e) {
            throw new AiProviderException("Could not parse Claude's uncertainty/questions response: " + e.getMessage(), e);
        }
        return new UncertaintyAndQuestions(toItems(root.path("uncertainties")), toItems(root.path("questions")));
    }

    private List<BriefingItem> toItems(JsonNode array) {
        List<BriefingItem> items = new ArrayList<>();
        for (JsonNode node : array) {
            String description = node.path("description").asText("");
            if (description.isBlank()) {
                continue;
            }
            items.add(BriefingItem.of(description, node.path("entity").asText(null)));
        }
        return items;
    }

    private JsonNode send(String prompt) {
        ObjectNode payload = json.createObjectNode();
        payload.put("model", MODEL);
        payload.put("max_tokens", 1024);
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
