package com.athena.reviewreplay;

import com.athena.semantic.Change;
import com.athena.semantic.DetectedTransformation;
import com.athena.semantic.ModuleGrouper;

import java.util.List;
import java.util.Optional;

/**
 * Resolves an {@code entity:<name>} reference to the module/territory the
 * Semantic Canvas can navigate to (ticket #212), by finding the {@link
 * Change} in the current Diff whose {@link Change#enclosingType()}
 * matches the name and running its first touched file through {@link
 * ModuleGrouper#moduleOf(String)} — the exact same leading-path-segment
 * rule {@code ModuleTopologyController} already uses to build the
 * canvas's own territories, so a resolved module name is always one the
 * canvas actually recognizes. No new module-boundary logic: {@code
 * enclosingType()} and a Change's touched files are both derived from
 * the same first matched occurrence (see {@link Change#enclosingType()}'s
 * own Javadoc), so the two are always consistent for the same Change.
 */
public final class EntityModuleResolver {

    private EntityModuleResolver() {
    }

    /** The module the entity named {@code entityName} lives in, if a Change in {@code changes} concerns it. */
    public static Optional<String> resolve(String entityName, List<Change> changes) {
        return changes.stream()
                .filter(change -> entityName.equals(change.enclosingType()))
                .findFirst()
                .flatMap(EntityModuleResolver::firstTouchedFile)
                .map(ModuleGrouper::moduleOf);
    }

    private static Optional<String> firstTouchedFile(Change change) {
        for (DetectedTransformation occurrence : change.matchedOccurrences()) {
            if (!occurrence.filesTouched().isEmpty()) {
                return Optional.of(occurrence.filesTouched().get(0));
            }
        }
        return Optional.empty();
    }
}
