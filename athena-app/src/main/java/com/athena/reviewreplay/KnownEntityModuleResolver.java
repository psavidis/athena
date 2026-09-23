package com.athena.reviewreplay;

import com.athena.semantic.Change;
import com.athena.semantic.ModuleGrouper;

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
    private final Supplier<ModuleGrouper> grouper;

    public KnownEntityModuleResolver(Supplier<List<Change>> currentChanges) {
        this(currentChanges, ModuleGrouper::new);
    }

    /** {@code grouper} names modules the way the Canvas does (ticket #292). */
    public KnownEntityModuleResolver(Supplier<List<Change>> currentChanges, Supplier<ModuleGrouper> grouper) {
        this.currentChanges = Objects.requireNonNull(currentChanges, "currentChanges");
        this.grouper = Objects.requireNonNull(grouper, "grouper");
    }

    @Override
    public Optional<String> resolveModule(String entityName) {
        return EntityModuleResolver.resolve(entityName, currentChanges.get(), grouper.get());
    }
}
