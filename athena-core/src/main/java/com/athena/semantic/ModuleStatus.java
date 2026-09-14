package com.athena.semantic;

/**
 * How much a module changed in this PR (ticket #129's territory-map visual
 * treatment): {@link #NEW} — the module didn't exist at the base revision;
 * {@link #TOUCHED} — it existed and this PR modified it; {@link #IDLE} — a
 * touched module depends on it, but this PR left it unchanged.
 */
public enum ModuleStatus {
    NEW,
    TOUCHED,
    IDLE
}
