package com.athena.contextrewind;

import java.util.List;

/**
 * The seam between "why does this entity matter, historically?" and an
 * actual AI provider (ticket #161) — mirrors
 * {@code com.athena.ai.ModuleNarrativeProvider}'s role for module
 * narratives, but summarizes an entity's already-aggregated historical
 * facts rather than a set of detected Changes. Advisory only: the
 * returned narrative is always presented as {@link ContextSource#AI_INTERPRETATION},
 * never a project fact in its own right.
 */
public interface ContextNarrativeProvider {

    String narrate(String entityName, List<String> historicalFacts);
}
