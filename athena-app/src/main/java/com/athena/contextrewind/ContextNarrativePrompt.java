package com.athena.contextrewind;

import java.util.List;

/**
 * The prompt shared by every way Athena can ask an AI provider to narrate
 * an entity's reconstructed history (ticket #161) — mirrors
 * {@code com.athena.ai.ModuleNarrativePrompt}'s role for module
 * narratives.
 */
final class ContextNarrativePrompt {

    private ContextNarrativePrompt() {
    }

    /**
     * Frames the request as inferring intent from the already-aggregated
     * historical facts alone — never as inventing history those facts
     * don't support.
     */
    static String build(String entityName, List<String> historicalFacts) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("A developer is trying to recover their understanding of \"").append(entityName)
                .append("\", a part of the codebase they haven't looked at in a while. Its known history is:\n\n");
        for (String fact : historicalFacts) {
            prompt.append("- ").append(fact).append("\n");
        }
        prompt.append("\nIn 1-3 sentences, explain why \"").append(entityName)
                .append("\" likely exists and how it has evolved — infer this only from the history above, ")
                .append("never invent facts it doesn't support. Respond with only the explanation itself, ")
                .append("no preamble, no markdown formatting.");
        return prompt.toString();
    }
}
