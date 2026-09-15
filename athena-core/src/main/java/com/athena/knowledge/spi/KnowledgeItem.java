package com.athena.knowledge.spi;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * One piece of contextual evidence retrieved from (or persisted to) a
 * {@link KnowledgeProvider} (ticket #118) — provider-independent: a note
 * read from an Obsidian vault and one persisted from a user-approved
 * {@link KnowledgeCandidate} are both represented the same way here.
 *
 * <p>Carries the minimum provenance the ticket's Functional Requirements
 * call for: {@link #source()}, {@link #createdAt()}, and
 * {@link #repositoryContext()}. {@code metadata} is the deliberate escape
 * hatch for future provenance (relevant files/symbols, related PRs,
 * author, confidence, validity period, superseded knowledge) without
 * widening this type's own contract.
 *
 * <p>Never authoritative — Athena's reasoning treats every item here as
 * contextual evidence that may be outdated, incomplete, or contradict the
 * current change, never as ground truth.
 */
public final class KnowledgeItem {

    private final String providerId;
    private final String title;
    private final String content;
    private final String source;
    private final Instant createdAt;
    private final String repositoryContext;
    private final Map<String, String> metadata;

    private KnowledgeItem(Builder builder) {
        this.providerId = builder.providerId;
        this.title = builder.title;
        this.content = builder.content;
        this.source = builder.source;
        this.createdAt = builder.createdAt;
        this.repositoryContext = builder.repositoryContext;
        this.metadata = Map.copyOf(builder.metadata);
    }

    public static Builder builder(String providerId, String title, String content, String source, Instant createdAt) {
        return new Builder(providerId, title, content, source, createdAt);
    }

    public String providerId() {
        return providerId;
    }

    public String title() {
        return title;
    }

    public String content() {
        return content;
    }

    /** Where within the provider this item came from (e.g. a note's path within its vault). */
    public String source() {
        return source;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** The project/repository this item is scoped to, or blank if provider-wide/unscoped. */
    public String repositoryContext() {
        return repositoryContext;
    }

    /** Provider-specific/future provenance, opaque to Athena's core. Empty if the provider supplies none. */
    public Map<String, String> metadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KnowledgeItem other)) return false;
        return providerId.equals(other.providerId)
                && title.equals(other.title)
                && content.equals(other.content)
                && source.equals(other.source)
                && createdAt.equals(other.createdAt)
                && repositoryContext.equals(other.repositoryContext)
                && metadata.equals(other.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId, title, content, source, createdAt, repositoryContext, metadata);
    }

    /** Builds a {@link KnowledgeItem}, per CODE_STYLE.md's builder guidance for a type with optional fields. */
    public static final class Builder {
        private final String providerId;
        private final String title;
        private final String content;
        private final String source;
        private final Instant createdAt;
        private String repositoryContext = "";
        private Map<String, String> metadata = Map.of();

        private Builder(String providerId, String title, String content, String source, Instant createdAt) {
            this.providerId = requireNonBlank(providerId, "providerId");
            this.title = requireNonBlank(title, "title");
            this.content = Objects.requireNonNull(content, "content");
            this.source = requireNonBlank(source, "source");
            this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        }

        public Builder repositoryContext(String repositoryContext) {
            this.repositoryContext = Objects.requireNonNull(repositoryContext, "repositoryContext");
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = Objects.requireNonNull(metadata, "metadata");
            return this;
        }

        public KnowledgeItem build() {
            return new KnowledgeItem(this);
        }

        private static String requireNonBlank(String value, String fieldName) {
            Objects.requireNonNull(value, fieldName);
            if (value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            return value;
        }
    }
}
