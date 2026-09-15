package com.athena.knowledge.spi;

import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of one {@link KnowledgeProvider#captureCandidate} call
 * (ticket #118): either the candidate was persisted — {@link #persistedItem()}
 * names the resulting {@link KnowledgeItem}, with its own provenance — or
 * it failed, carrying a reason. Never throws: a failed capture is reported
 * to the user as a rejected save action, not surfaced as an unhandled error.
 */
public final class KnowledgeCaptureResult {

    private final boolean successful;
    private final KnowledgeItem persistedItem;
    private final String failureReason;

    private KnowledgeCaptureResult(boolean successful, KnowledgeItem persistedItem, String failureReason) {
        this.successful = successful;
        this.persistedItem = persistedItem;
        this.failureReason = failureReason;
    }

    public static KnowledgeCaptureResult success(KnowledgeItem persistedItem) {
        Objects.requireNonNull(persistedItem, "persistedItem");
        return new KnowledgeCaptureResult(true, persistedItem, null);
    }

    public static KnowledgeCaptureResult failure(String reason) {
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return new KnowledgeCaptureResult(false, null, reason);
    }

    public boolean isSuccessful() {
        return successful;
    }

    /** The persisted item. Always empty when {@link #isSuccessful()} is false. */
    public Optional<KnowledgeItem> persistedItem() {
        return Optional.ofNullable(persistedItem);
    }

    /** Why this capture failed. Only meaningful when {@link #isSuccessful()} is false. */
    public String failureReason() {
        return failureReason;
    }
}
