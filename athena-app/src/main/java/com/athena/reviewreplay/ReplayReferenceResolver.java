package com.athena.reviewreplay;

import java.util.Optional;

/**
 * The Replay-facing contract (ticket #210) for resolving one of a
 * {@code ReviewRecordingArtifact}'s opaque entity/Change references
 * against the current codebase. {@code SemanticEvent}/{@code Moment}
 * deliberately relay a reference without interpreting it (see their own
 * Javadoc); Replay is the first thing that needs to interpret one, so it
 * owns this seam rather than reaching into the recorder's internals.
 *
 * <p>A reference that no longer resolves (e.g. the entity was renamed or
 * deleted since the recording) is a normal, expected outcome — represented
 * by an empty {@link Optional}, not an exception — so opening a Replay
 * never fails just because one of its references has gone stale.
 */
public interface ReplayReferenceResolver {

    /**
     * Resolves {@code reference} to a current, human-readable label, if it
     * still identifies something in the current codebase.
     */
    Optional<String> resolve(String reference);
}
