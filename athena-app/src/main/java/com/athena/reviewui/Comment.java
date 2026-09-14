package com.athena.reviewui;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A reviewer's comment at some {@link AnnotationScope}. Comments are the
 * kind of annotation that syncs to GitHub — storage and the sync-exclusion
 * guarantee (which distinguishes this from {@link PrivateNote}) belong to
 * epic #6; this is the UI-facing shape epic #6's storage should mirror.
 *
 * <p>Immutable per this codebase's design principles: editing (ticket #134)
 * is {@link AnnotationBoard} replacing the {@link Comment} at this id with a
 * new instance carrying the edited text, not mutating this one in place.
 */
public final class Comment {

    private final String id;
    private final AnnotationScope scope;
    private final String author;
    private final Instant postedAt;
    private final String text;

    Comment(AnnotationScope scope, String author, Instant postedAt, String text) {
        this(UUID.randomUUID().toString(), scope, author, postedAt, text);
    }

    private Comment(String id, AnnotationScope scope, String author, Instant postedAt, String text) {
        this.id = id;
        this.scope = scope;
        this.author = author;
        this.postedAt = postedAt;
        this.text = text;
    }

    /** A copy of this comment with its text replaced, keeping the same id/scope/author/postedAt. */
    Comment withText(String newText) {
        return new Comment(id, scope, author, postedAt, newText);
    }

    public String id() {
        return id;
    }

    public AnnotationScope scope() {
        return scope;
    }

    public String author() {
        return author;
    }

    public Instant postedAt() {
        return postedAt;
    }

    public String text() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Comment other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
