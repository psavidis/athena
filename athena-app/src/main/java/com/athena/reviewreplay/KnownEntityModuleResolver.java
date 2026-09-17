package com.athena.reviewreplay;

import com.athena.semantic.Change;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Resolves an entity name to its module by matching it against the
 * current Diff's analyzed {@link Change}s (ticket #212), delegating the
 * actual module derivation to {@link EntityModuleResolver}. A {@link
 * Supplier} rather than a plain {@code List<Change>}, matching {@link
 * KnownEntityReferenceResolver}'s own precedent, so this resolver is
 * cheap to construct even when the Diff's analysis hasn't run yet.
 */
public final class KnownEntityModuleResolver implements ReplayModuleResolver {

    private final Supplier<List<Change>> currentChanges;

    public KnownEntityModuleResolver(Supplier<List<Change>> currentChanges) {
        this.currentChanges = Objects.requireNonNull(currentChanges, "currentChanges");
    }

    @Override
    public Optional<String> resolveModule(String entityName) {
        return EntityModuleResolver.resolve(entityName, currentChanges.get());
    }
}
