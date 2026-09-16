package com.athena.reviewreplay;

import java.util.Objects;
import java.util.Optional;

/**
 * One {@code ReviewRecordingArtifact} reference (ticket #210), as resolved
 * by a {@link ReplayReferenceResolver} against the current codebase:
 * either its current label, or unresolved if it no longer identifies
 * anything (e.g. a rename or deletion since the recording).
 */
public final class ResolvedReference {

    private final String reference;
    private final String resolvedLabel;

    private ResolvedReference(String reference, String resolvedLabel) {
        this.reference = reference;
        this.resolvedLabel = resolvedLabel;
    }

    static ResolvedReference resolve(String reference, ReplayReferenceResolver resolver) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(resolver, "resolver");
        return new ResolvedReference(reference, resolver.resolve(reference).orElse(null));
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
}
