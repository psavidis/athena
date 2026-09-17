package com.athena.reviewreplay;

import java.util.Optional;

/**
 * The Replay-facing contract (ticket #212) for resolving a still-resolving
 * {@code entity:<name>} reference to the module/territory the Semantic
 * Canvas can navigate to. Deliberately separate from {@link
 * ReplayReferenceResolver}: a reference can resolve to a current label
 * while its module still can't be determined (e.g. the entity isn't part
 * of this Diff's analyzed Changes), which is a normal, expected outcome —
 * represented by an empty {@link Optional}, not an exception.
 */
public interface ReplayModuleResolver {

    /** The module {@code entityName} lives in, if it can be determined from the current Diff. */
    Optional<String> resolveModule(String entityName);
}
