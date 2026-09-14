package com.athena.reviewui;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Holds comments and private notes per {@link AnnotationScope} for display
 * (epic #5 §21, §22). In-memory and UI-facing only — durable storage, the
 * Review Context artifact, and the GitHub-sync-exclusion guarantee for
 * private notes belong to epic #6, which should mirror this same
 * scope/Comment/PrivateNote shape for its own storage layer.
 */
public final class AnnotationBoard {

    private final Map<AnnotationScope, List<Comment>> commentsByScope = new LinkedHashMap<>();
    private final Map<AnnotationScope, List<PrivateNote>> notesByScope = new LinkedHashMap<>();

    public void addComment(AnnotationScope scope, String author, String text) {
        Comment comment = new Comment(scope, requireText(author, "author"), Instant.now(), requireText(text, "text"));
        commentsByScope.computeIfAbsent(scope, s -> new ArrayList<>()).add(comment);
    }

    /**
     * Replaces the comment's text in place (same id/scope/author/postedAt,
     * ticket #134) — the comment is immutable, so this swaps in a new
     * instance at the same list position rather than mutating it.
     */
    public void editComment(AnnotationScope scope, String commentId, String newText) {
        List<Comment> comments = commentsByScope.getOrDefault(scope, List.of());
        int index = indexOfComment(comments, commentId);
        comments.set(index, comments.get(index).withText(requireText(newText, "text")));
    }

    /** Removes a comment by id (ticket #134). */
    public void deleteComment(AnnotationScope scope, String commentId) {
        List<Comment> comments = commentsByScope.getOrDefault(scope, List.of());
        int index = indexOfComment(comments, commentId);
        comments.remove(index);
    }

    private int indexOfComment(List<Comment> comments, String commentId) {
        for (int i = 0; i < comments.size(); i++) {
            if (comments.get(i).id().equals(commentId)) {
                return i;
            }
        }
        throw new NoSuchElementException("No comment with id " + commentId + " at this scope");
    }

    public void addPrivateNote(AnnotationScope scope, String text) {
        notesByScope.computeIfAbsent(scope, s -> new ArrayList<>()).add(new PrivateNote(scope, requireText(text, "text")));
    }

    private String requireText(String text, String fieldName) {
        Objects.requireNonNull(text, fieldName);
        if (text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }

    /** Comments at this scope, in the order they were added. Empty if none. */
    public List<Comment> commentsAt(AnnotationScope scope) {
        return List.copyOf(commentsByScope.getOrDefault(scope, List.of()));
    }

    /** Private notes at this scope, in the order they were added. Empty if none. */
    public List<PrivateNote> privateNotesAt(AnnotationScope scope) {
        return List.copyOf(notesByScope.getOrDefault(scope, List.of()));
    }

    /**
     * Every stored comment, across every scope. This method's return type is the
     * structural guarantee behind the GitHub-sync-exclusion rule (epic #6 §22): it
     * is impossible for a caller to obtain a {@link PrivateNote} through this
     * method, so a sync path built only on {@code allComments()} cannot leak one
     * even by accident — there is no private-note-shaped data reachable from here.
     */
    public List<Comment> allComments() {
        return commentsByScope.values().stream().flatMap(List::stream).toList();
    }

    /**
     * Every stored private note, across every scope. Unlike {@link #allComments()},
     * this is explicitly opt-in for any consumer that assembles a shareable
     * artifact (e.g. the Review Context, epic #6 #44) — private notes must never
     * be included by default.
     */
    public List<PrivateNote> allPrivateNotes() {
        return notesByScope.values().stream().flatMap(List::stream).toList();
    }
}
