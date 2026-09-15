package com.athena.knowledge.spi;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of one {@link KnowledgeProvider#retrieveRelevant} call
 * (ticket #118) — mirrors {@code com.athena.analysis.spi.ProviderRunResult}'s
 * role for {@code ExternalAnalysisProvider}: either a successful retrieval
 * (possibly with no items, if nothing was relevant) or a failure, carrying
 * a reason but no items. A caller isolates one provider's failure from
 * the rest of the review completing — a failed result never throws, and
 * never blocks the review (the ticket's graceful-degradation requirement).
 */
public final class KnowledgeRetrievalResult {

    private final boolean successful;
    private final List<KnowledgeItem> items;
    private final String failureReason;

    private KnowledgeRetrievalResult(boolean successful, List<KnowledgeItem> items, String failureReason) {
        this.successful = successful;
        this.items = List.copyOf(items);
        this.failureReason = failureReason;
    }

    /** A successful retrieval, with whatever items the provider found relevant (possibly none). */
    public static KnowledgeRetrievalResult success(List<KnowledgeItem> items) {
        Objects.requireNonNull(items, "items");
        return new KnowledgeRetrievalResult(true, items, null);
    }

    /** A failed retrieval — the provider could not complete (e.g. its vault is unreachable). */
    public static KnowledgeRetrievalResult failure(String reason) {
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return new KnowledgeRetrievalResult(false, List.of(), reason);
    }

    public boolean isSuccessful() {
        return successful;
    }

    /** This retrieval's items. Always empty when {@link #isSuccessful()} is false. */
    public List<KnowledgeItem> items() {
        return items;
    }

    /** Why this retrieval failed. Only meaningful when {@link #isSuccessful()} is false. */
    public String failureReason() {
        return failureReason;
    }
}
