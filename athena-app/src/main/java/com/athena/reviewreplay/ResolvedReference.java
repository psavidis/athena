package com.athena.reviewreplay;

import java.util.Objects;
import java.util.Optional;

/**
 * One {@code ReviewRecordingArtifact} reference (ticket #210), as resolved
 * by a {@link ReplayReferenceResolver} against the current codebase:
 * either its current label, or unresolved if it no longer identifies
 * anything (e.g. a rename or deletion since the recording). Also carries
 * the module the reference's entity belongs to (ticket #212), resolved
 * only when the reference itself still resolves — an unresolved reference
 * (renamed/deleted since the recording) never has a determinable module
 * either, per #212's own "reference no longer resolves" scenario.
 */
public final class ResolvedReference {

    private static final String ENTITY_PREFIX = "entity:";

    private final String reference;
    private final String resolvedLabel;
    private final String module;

    private ResolvedReference(String reference, String resolvedLabel, String module) {
        this.reference = reference;
        this.resolvedLabel = resolvedLabel;
        this.module = module;
    }

    static ResolvedReference resolve(String reference, ReplayReferenceResolver resolver, ReplayModuleResolver moduleResolver) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(moduleResolver, "moduleResolver");
        String resolvedLabel = resolver.resolve(reference).orElse(null);
        String module = (resolvedLabel != null && reference.startsWith(ENTITY_PREFIX))
                ? moduleResolver.resolveModule(reference.substring(ENTITY_PREFIX.length())).orElse(null)
                : null;
        return new ResolvedReference(reference, resolvedLabel, module);
    }

    public String reference() {
        return reference;
    }

    public boolean resolved() {
        return resolvedLabel != null;
    }

    /** The reference's current label, if it still resolves. */
    public Optional<String> resolvedLabel() {
        return Optional.ofNullable(resolvedLabel);
    }

    /** The module the reference's entity belongs to, if it could be determined. */
    public Optional<String> module() {
        return Optional.ofNullable(module);
    }
}
