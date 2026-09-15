package com.athena.knowledge.spi;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Something Athena has identified as possibly worth remembering for
 * future reviews (ticket #118) — e.g. an accepted AI finding the user
 * chooses to persist. Never written automatically: a
 * {@link KnowledgeProvider#captureCandidate} call always represents a
 * user-confirmed action, matching the ticket's "prefer user confirmation
 * before persistent knowledge is created" requirement.
 */
public final class KnowledgeCandidate {

    private final String repositoryContext;
    private final String content;
    private final String rationale;
    private final Instant proposedAt;

    private KnowledgeCandidate(String repositoryContext, String content, String rationale, Instant proposedAt) {
        this.repositoryContext = repositoryContext;
        this.content = content;
        this.rationale = rationale;
        this.proposedAt = proposedAt;
    }

    public static KnowledgeCandidate of(String repositoryContext, String content, Instant proposedAt) {
        return new KnowledgeCandidate(
                Objects.requireNonNull(repositoryContext, "repositoryContext"),
                requireNonBlank(content),
                null,
                Objects.requireNonNull(proposedAt, "proposedAt"));
    }

    public static KnowledgeCandidate withRationale(String repositoryContext, String content, String rationale,
                                                    Instant proposedAt) {
        return new KnowledgeCandidate(
                Objects.requireNonNull(repositoryContext, "repositoryContext"),
                requireNonBlank(content),
                Objects.requireNonNull(rationale, "rationale"),
                Objects.requireNonNull(proposedAt, "proposedAt"));
    }

    public String repositoryContext() {
        return repositoryContext;
    }

    public String content() {
        return content;
    }

    /** Why this candidate was proposed (e.g. the finding it came from). Empty if none was given. */
    public Optional<String> rationale() {
        return Optional.ofNullable(rationale);
    }

    public Instant proposedAt() {
        return proposedAt;
    }

    private static String requireNonBlank(String content) {
        Objects.requireNonNull(content, "content");
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KnowledgeCandidate other)) return false;
        return repositoryContext.equals(other.repositoryContext)
                && content.equals(other.content)
                && Objects.equals(rationale, other.rationale)
                && proposedAt.equals(other.proposedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repositoryContext, content, rationale, proposedAt);
    }
}
