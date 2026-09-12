package com.athena.ai;

import com.athena.reviewcontext.ReviewContext;
import com.athena.semantic.Change;
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
import java.util.UUID;

/**
 * Production {@link AiProvider} backed by the Anthropic Messages API
 * (https://api.anthropic.com), using the JDK's built-in HttpClient and
 * Jackson for JSON. This is epic #7 #46's one required concrete provider —
 * a second (e.g. Gemini) can be added later behind {@link AiProvider}
 * without changing {@link ReviewContext} or any caller.
 */
public class ClaudeAiProvider implements AiProvider {

    private static final URI API_BASE = URI.create("https://api.anthropic.com/v1/messages");
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String MODEL = "claude-sonnet-5";

    private final HttpClient httpClient;
    private final String apiKey;
    private final ObjectMapper json = new ObjectMapper();

    public ClaudeAiProvider(HttpClient httpClient, String apiKey) {
        this.httpClient = httpClient;
        this.apiKey = apiKey;
    }

    @Override
    public List<AiFinding> analyze(ReviewContext reviewContext) {
        JsonNode responseBody = send(buildPrompt(reviewContext));
        return parseFindings(responseBody);
    }

    /** Frames the request as "what might the human have missed," never "review this PR" cold (§35). */
    private String buildPrompt(ReviewContext reviewContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("A human reviewer has completed their own review of the pull request \"")
                .append(reviewContext.prTitle())
                .append("\". Given what they already reviewed and understood, point out anything they ")
                .append("may have missed. Do not re-review the whole PR from scratch.\n\n");
        appendChangeList(prompt, "Reviewed", reviewContext.reviewedChanges());
        appendChangeList(prompt, "Classified mechanical (not individually reviewed)", reviewContext.mechanicalChanges());
        appendChangeList(prompt, "Skipped", reviewContext.skippedChanges());
        appendChangeList(prompt, "Flagged as a concern", reviewContext.concernChanges());
        prompt.append("Coverage: ").append(reviewContext.coverageSummary()).append("\n\n");
        prompt.append("Respond with only a JSON array of findings, each an object with an \"id\" and a ")
                .append("\"description\" field. Respond with an empty array if there is nothing to add.");
        return prompt.toString();
    }

    private void appendChangeList(StringBuilder prompt, String label, List<Change> changes) {
        if (changes.isEmpty()) {
            return;
        }
        prompt.append(label).append(":\n");
        for (Change change : changes) {
            prompt.append("- ").append(change).append('\n');
        }
        prompt.append('\n');
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

    private List<AiFinding> parseFindings(JsonNode responseBody) {
        String text = responseBody.path("content").path(0).path("text").asText("[]");
        List<AiFinding> findings = new ArrayList<>();
        JsonNode findingsArray;
        try {
            findingsArray = json.readTree(text);
        } catch (IOException e) {
            throw new AiProviderException("Could not parse Claude's findings: " + e.getMessage(), e);
        }
        for (JsonNode findingNode : findingsArray) {
            String id = findingNode.hasNonNull("id") ? findingNode.path("id").asText() : UUID.randomUUID().toString();
            findings.add(new AiFinding(id, findingNode.path("description").asText()));
        }
        return findings;
    }
}
