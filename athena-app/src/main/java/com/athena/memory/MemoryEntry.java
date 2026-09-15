package com.athena.memory;

/**
 * One fact Athena has learned about a project (ticket #169), together with the
 * provenance that backs it. {@code developerConfirmed} distinguishes a fact a
 * developer has explicitly confirmed from a pattern Athena only inferred from
 * historical evidence — memory must never present the latter with the
 * authority of the former (see epic #121, "Memory Must Not Turn Historical
 * Behavior Into Rules").
 */
public record MemoryEntry(String fact, String evidence, String confidence, boolean developerConfirmed) {
}
