package com.athena.ai;

import com.athena.semantic.ModuleGroup;

/**
 * The seam between "why was this module touched?" and an actual AI
 * provider — deliberately separate from {@link AiProvider} (which answers
 * "what might the human reviewer have missed" against a completed
 * {@code ReviewContext}): a module narrative is generated up front, before
 * any review has happened, and never claims the deterministic certainty
 * a {@link com.athena.semantic.spi.LanguagePlugin}'s own output does.
 */
public interface ModuleNarrativeProvider {

    /**
     * A short, human-readable explanation of why the given module's Changes
     * were made, inferred from their titles/kinds/files alone — advisory,
     * not authoritative; the human reviewer's own reading of the diff
     * remains the source of truth.
     */
    String explain(ModuleGroup moduleGroup);
}
