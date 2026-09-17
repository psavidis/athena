package com.athena.reviewbriefing;

import com.athena.semantic.Change;
import com.athena.semantic.SemanticClassification;
import com.athena.semantic.SemanticDimension;
import com.athena.semantic.SemanticProfile;

import java.util.List;

/**
 * The prompt-building for asking Claude to summarize a PR's whole set of
 * detected Changes in plain language (ticket #219) — mirrors {@code
 * com.athena.ai.ModuleNarrativePrompt}'s own structure, but draws on each
 * Change's full {@link SemanticProfile} (every classified dimension, not
 * just title/kind) so the summary can mention affected components,
 * architectural layers, and responsibility shifts.
 */
final class SemanticChangeSummaryPrompt {

    private SemanticChangeSummaryPrompt() {
    }

    static String build(List<Change> changes, List<SemanticProfile> profiles) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("A code reviewer is opening a pull request. It contains the following detected Changes, ")
                .append("each with its own semantic classification:\n\n");
        for (int i = 0; i < changes.size(); i++) {
            Change change = changes.get(i);
            SemanticProfile profile = profiles.get(i);
            prompt.append("- ").append(change.title()).append(" (").append(change.kind()).append(")");
            for (SemanticDimension dimension : SemanticDimension.values()) {
                List<SemanticClassification> classifications = profile.classifications(dimension);
                if (!classifications.isEmpty()) {
                    prompt.append("\n  ").append(dimension).append(": ");
                    prompt.append(String.join(", ", classifications.stream()
                            .map(c -> c.concept().name()).toList()));
                }
            }
            prompt.append("\n");
        }
        prompt.append("\nIn 1-2 sentences, explain in plain business/engineering terms what this PR's Changes ")
                .append("accomplish together, mentioning the affected components, architectural layers, or ")
                .append("responsibility shifts the classifications above indicate — infer this only from the ")
                .append("data above, not from information you don't have. Respond with only the summary ")
                .append("itself, no preamble, no markdown formatting.");
        return prompt.toString();
    }
}
