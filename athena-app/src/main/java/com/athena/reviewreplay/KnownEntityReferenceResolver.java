package com.athena.reviewreplay;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Resolves an {@code entity:<name>} reference (the recorder's own
 * convention — see {@code ReviewRecordingSessionSteps}'s {@code
 * inspects_the_entity} step) by checking whether {@code name} is still
 * among the entities {@link #currentEntityNames} reports for the
 * artifact's repository/commit right now. A non-{@code entity:} reference
 * (e.g. a future Change or commit reference) never resolves here — this
 * resolver only knows about entities; ticket #210 defines the contract,
 * not every reference kind Replay may eventually need to resolve.
 */
public final class KnownEntityReferenceResolver implements ReplayReferenceResolver {

    private static final String ENTITY_PREFIX = "entity:";

    private final Supplier<Set<String>> currentEntityNames;

    public KnownEntityReferenceResolver(Supplier<Set<String>> currentEntityNames) {
        this.currentEntityNames = Objects.requireNonNull(currentEntityNames, "currentEntityNames");
    }

    @Override
    public Optional<String> resolve(String reference) {
        if (reference == null || !reference.startsWith(ENTITY_PREFIX)) {
            return Optional.empty();
        }
        String entityName = reference.substring(ENTITY_PREFIX.length());
        return currentEntityNames.get().contains(entityName) ? Optional.of(entityName) : Optional.empty();
    }
}
