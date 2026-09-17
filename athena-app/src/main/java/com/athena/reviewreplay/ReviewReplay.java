package com.athena.reviewreplay;

import com.athena.reviewrecorder.Moment;
import com.athena.reviewrecorder.ReviewRecordingArtifact;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A persisted {@link ReviewRecordingArtifact} opened for replay (ticket
 * #210): its identity (repository/PR/commit), unchanged from the
 * artifact, plus each of its moments' references resolved against the
 * current codebase via a {@link ReplayReferenceResolver}. No timeline UI
 * or transcript handling yet — those are later tickets (#211+).
 */
public final class ReviewReplay {

    private final ReviewRecordingArtifact artifact;
    private final Map<String, ResolvedReference> resolvedReferencesByReference;

    private ReviewReplay(ReviewRecordingArtifact artifact, Map<String, ResolvedReference> resolvedReferencesByReference) {
        this.artifact = artifact;
        this.resolvedReferencesByReference = resolvedReferencesByReference;
    }

    /** Opens {@code artifact} for replay, resolving every distinct moment reference it carries via {@code resolver}. */
    public static ReviewReplay open(ReviewRecordingArtifact artifact, ReplayReferenceResolver resolver) {
        return open(artifact, resolver, entityName -> Optional.empty());
    }

    /**
     * Opens {@code artifact} for replay, resolving every distinct moment
     * reference via {@code resolver} and, for each that still resolves,
     * its module via {@code moduleResolver} (ticket #212).
     */
    public static ReviewReplay open(ReviewRecordingArtifact artifact, ReplayReferenceResolver resolver,
                                     ReplayModuleResolver moduleResolver) {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(moduleResolver, "moduleResolver");
        Map<String, ResolvedReference> resolved = new LinkedHashMap<>();
        for (Moment moment : artifact.moments()) {
            String reference = moment.reference();
            if (reference != null) {
                resolved.computeIfAbsent(reference, ref -> ResolvedReference.resolve(ref, resolver, moduleResolver));
            }
        }
        return new ReviewReplay(artifact, Map.copyOf(resolved));
    }

    public String recordingId() {
        return artifact.recordingId();
    }

    public String repositoryFullName() {
        return artifact.repositoryFullName();
    }

    public int pullRequestNumber() {
        return artifact.pullRequestNumber();
    }

    public String commitOrVersion() {
        return artifact.commitOrVersion();
    }

    /** Every distinct moment reference this Replay resolved, in first-seen order. */
    public List<ResolvedReference> resolvedReferences() {
        return List.copyOf(resolvedReferencesByReference.values());
    }

    /** How {@code reference} resolved, if it was among this Replay's moment references. */
    public Optional<ResolvedReference> resolvedReference(String reference) {
        return Optional.ofNullable(resolvedReferencesByReference.get(reference));
    }
}
