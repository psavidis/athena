package com.athena.contextrewind;

/**
 * What kind of evidence a {@link ContextFact} rests on (ticket #161) — a
 * reconstructed context must never present these with equal authority:
 * {@link #HISTORY} is derived from git/GitHub, {@link #KNOWLEDGE_BASE} is
 * explicit knowledge a human maintains outside the codebase, and
 * {@link #AI_INTERPRETATION} is Athena's own inference, never a fact in
 * its own right.
 */
public enum ContextSource {
    HISTORY,
    KNOWLEDGE_BASE,
    AI_INTERPRETATION
}
