package com.athena.ai;

import com.athena.semantic.Change;
import com.athena.semantic.ModuleGroup;

/**
 * The prompt-building shared by every way Athena can ask Claude to explain
 * a module's Changes ({@link ClaudeModuleNarrativeProvider} over the direct
 * Messages API, {@link ClaudeCliModuleNarrativeProvider} over the
 * {@code claude} CLI) — only how the prompt is actually sent differs.
 */
final class ModuleNarrativePrompt {

    private ModuleNarrativePrompt() {
    }

    /**
     * Frames the request as inferring intent from structural facts alone —
     * titles, kinds, files — never as re-deriving certainty the deterministic
     * engine didn't itself produce.
     */
    static String build(ModuleGroup moduleGroup) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("A code reviewer is looking at a pull request. The module \"")
                .append(moduleGroup.moduleName())
                .append("\" contains the following detected Changes:\n\n");
        for (Change change : moduleGroup.changes()) {
            prompt.append("- ").append(change.title())
                    .append(" (").append(change.kind()).append(")\n");
        }
        prompt.append("\nIn 1-3 sentences, explain in plain business/engineering terms what this module's ")
                .append("Changes likely accomplish together and why they might belong together — infer this ")
                .append("only from the titles and kinds above, not from information you don't have. Respond ")
                .append("with only the explanation itself, no preamble, no markdown formatting.");
        return prompt.toString();
    }
}
