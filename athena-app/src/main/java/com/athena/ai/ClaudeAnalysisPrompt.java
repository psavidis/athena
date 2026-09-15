package com.athena.ai;

import com.athena.knowledge.spi.KnowledgeItem;
import com.athena.reviewcontext.ReviewContext;
import com.athena.semantic.Change;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The prompt-building and response-parsing shared by every way Athena can
 * ask Claude to analyze a review ({@link ClaudeAiProvider} over the direct
 * Messages API, {@link ClaudeCliProvider} over the {@code claude} CLI) —
 * only how the prompt is actually sent differs between them.
 */
final class ClaudeAnalysisPrompt {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ClaudeAnalysisPrompt() {
    }

    /** Frames the request as "what might the human have missed," never "review this PR" cold (§35). */
    static String build(ReviewContext reviewContext) {
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
        appendProjectKnowledge(prompt, reviewContext.knowledgeItems());
        prompt.append("Respond with only a JSON array of findings, each an object with an \"id\" and a ")
                .append("\"description\" field. When a finding is about one specific Change listed above, ")
                .append("also include a \"relatedChangeTitle\" field containing that Change's exact quoted ")
                .append("title from above, verbatim — omit this field for a general finding not tied to one ")
                .append("Change. Respond with an empty array if there is nothing to add.");
        return prompt.toString();
    }

    /**
     * Appends retrieved project knowledge (ticket #118) as its own labeled section, framed
     * explicitly as contextual evidence rather than ground truth — knowledge must never be
     * treated as authoritative, and any conflict with the change should be surfaced as a
     * finding rather than silently resolved either way. Omitted entirely when there is no
     * knowledge to include (no Knowledge Provider configured, or nothing relevant was found),
     * so a review with no Knowledge Provider produces exactly the same prompt as before this
     * ticket.
     */
    private static void appendProjectKnowledge(StringBuilder prompt, List<KnowledgeItem> knowledgeItems) {
        if (knowledgeItems.isEmpty()) {
            return;
        }
        prompt.append("Project Knowledge (contextual evidence from the project's knowledge base — it may be ")
                .append("outdated, incomplete, or contradict this change; treat it as supporting context, never as ")
                .append("authoritative, and if it conflicts with the change, say so explicitly as a finding):\n");
        for (KnowledgeItem item : knowledgeItems) {
            prompt.append("- \"").append(item.title()).append("\": ").append(item.content()).append('\n');
        }
        prompt.append('\n');
    }

    private static void appendChangeList(StringBuilder prompt, String label, List<Change> changes) {
        if (changes.isEmpty()) {
            return;
        }
        prompt.append(label).append(":\n");
        for (Change change : changes) {
            prompt.append("- \"").append(change.title()).append("\" (")
                    .append(change.occurrenceCount()).append(" occurrences, ")
                    .append(change.exceptionCount()).append(" exceptions)\n");
        }
        prompt.append('\n');
    }

    /** Parses Claude's raw text reply (expected to be a JSON findings array, possibly code-fenced) into {@link AiFinding}s. */
    static List<AiFinding> parseFindings(String responseText) {
        String text = stripCodeFence(responseText);
        List<AiFinding> findings = new ArrayList<>();
        JsonNode findingsArray;
        try {
            findingsArray = JSON.readTree(text);
        } catch (IOException e) {
            throw new AiProviderException("Could not parse Claude's findings: " + e.getMessage(), e);
        }
        for (JsonNode findingNode : findingsArray) {
            String id = findingNode.hasNonNull("id") ? findingNode.path("id").asText() : UUID.randomUUID().toString();
            Optional<String> relatedChangeTitle = findingNode.hasNonNull("relatedChangeTitle")
                    ? Optional.of(findingNode.path("relatedChangeTitle").asText())
                    : Optional.empty();
            findings.add(new AiFinding(id, findingNode.path("description").asText(), relatedChangeTitle));
        }
        return findings;
    }

    /**
     * Claude often wraps a requested JSON answer in a markdown code fence (```json ... ```)
     * despite being asked for "only" the JSON — strip one if present before parsing, rather than
     * failing the whole analysis over formatting Claude didn't strictly follow.
     */
    private static String stripCodeFence(String text) {
        String trimmed = text.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        String afterOpeningFence = firstNewline >= 0 ? trimmed.substring(firstNewline + 1) : "";
        int closingFence = afterOpeningFence.lastIndexOf("```");
        return (closingFence >= 0 ? afterOpeningFence.substring(0, closingFence) : afterOpeningFence).strip();
    }
}
