package com.athena.knowledge.spi;

/**
 * A pluggable external source of project knowledge (ticket #118): the
 * seam between Athena and a human-maintained knowledge base like an
 * Obsidian vault. Mirrors {@code com.athena.analysis.spi.ExternalAnalysisProvider}'s
 * role for static-analysis tools — Athena's core consumes only this
 * interface and its provider-independent types, so adding a future
 * provider (Confluence, Notion, a Markdown/Git repository, ...) never
 * requires changing Athena's review pipeline.
 *
 * <p>Neither method may throw for an ordinary failure (the vault path
 * doesn't exist, a file can't be read, the provider is unreachable) — such
 * failures are reported via a failed {@link KnowledgeRetrievalResult}/
 * {@link KnowledgeCaptureResult}, so a caller can isolate one provider's
 * failure without a try/catch per call, and a review always completes
 * even when its Knowledge Provider can't.
 */
public interface KnowledgeProvider {

    /** A short, stable identifier for this provider, e.g. {@code "obsidian"}. */
    String providerId();

    /** Retrieves the knowledge relevant to {@code query} — never the provider's entire knowledge base. */
    KnowledgeRetrievalResult retrieveRelevant(KnowledgeQuery query, KnowledgeProviderConfiguration configuration);

    /** Persists a user-approved {@link KnowledgeCandidate} back to this provider. */
    KnowledgeCaptureResult captureCandidate(KnowledgeCandidate candidate, KnowledgeProviderConfiguration configuration);
}
