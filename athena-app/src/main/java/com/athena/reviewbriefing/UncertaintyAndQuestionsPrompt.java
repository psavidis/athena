package com.athena.reviewbriefing;

import com.athena.semantic.Change;
import com.athena.semantic.SemanticClassification;
import com.athena.semantic.SemanticDimension;
import com.athena.semantic.SemanticProfile;

import java.util.List;

/**
 * The prompt-building for asking Claude which of a PR's Changes it can't
 * confidently explain, and what questions a reviewer should ask (ticket
 * #221) — mirrors {@link SemanticChangeSummaryPrompt}'s (#219) own
 * per-Change classification listing, but asks for structured JSON output
 * instead of free text, since the result is two lists of entity-tied
 * items rather than one narrative.
 */
final class UncertaintyAndQuestionsPrompt {

    private UncertaintyAndQuestionsPrompt() {
    }

    static String build(List<Change> changes, List<SemanticProfile> profiles) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("A code reviewer is looking at a pull request. It contains the following detected ")
                .append("Changes, each with its own semantic classification:\n\n");
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
        prompt.append("\nFor each Change above whose purpose or motivation you cannot confidently explain from ")
                .append("this data alone, name the uncertainty and, separately, suggest one investigation ")
                .append("question a reviewer could ask to resolve it. Do not force an uncertainty or question ")
                .append("for a Change that's already clear from its title and classification — an empty list is ")
                .append("a valid, expected answer for a PR that's fully explainable. Respond with only a JSON ")
                .append("object of this exact shape, no preamble, no markdown formatting:\n")
                .append("{\"uncertainties\": [{\"description\": \"...\", \"entity\": \"...\"}], ")
                .append("\"questions\": [{\"description\": \"...\", \"entity\": \"...\"}]}\n")
                .append("\"entity\" is the enclosing type/class name the item concerns, or an empty string if none applies.");
        return prompt.toString();
    }
}
