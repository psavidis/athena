package com.athena.reviewbriefing;

import com.athena.ai.AiProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a Claude response into {@link UncertaintyAndQuestions} (ticket
 * #221) — split out from {@link ClaudeUncertaintyAndQuestionsProvider}
 * so this parsing logic (unlike the HTTP call itself) can be unit-tested
 * directly without a real or faked network boundary.
 */
final class UncertaintyAndQuestionsResponseParser {

    private static final ObjectMapper JSON = new ObjectMapper();

    private UncertaintyAndQuestionsResponseParser() {
    }

    static UncertaintyAndQuestions parse(String text) {
        JsonNode root;
        try {
            root = JSON.readTree(stripMarkdownCodeFence(text));
        } catch (IOException e) {
            throw new AiProviderException(
                    "Could not parse Claude's uncertainty/questions response: " + e.getMessage(), e);
        }
        return new UncertaintyAndQuestions(toItems(root.path("uncertainties")), toItems(root.path("questions")));
    }

    /**
     * The prompt asks for "no markdown formatting," but a model can still wrap its JSON in a
     * ```json ... ``` fence despite that instruction — strip one if present before parsing,
     * rather than letting a cosmetic wrapper turn into a hard parse failure.
     */
    static String stripMarkdownCodeFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        int firstNewline = text.indexOf('\n');
        int fenceEnd = text.lastIndexOf("```");
        if (firstNewline < 0 || fenceEnd <= firstNewline) {
            return text;
        }
        return text.substring(firstNewline + 1, fenceEnd).strip();
    }

    private static List<BriefingItem> toItems(JsonNode array) {
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
}
