package com.athena.reviewui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public void addComment(AnnotationScope scope, String text) {
        commentsByScope.computeIfAbsent(scope, s -> new ArrayList<>()).add(new Comment(scope, requireText(text)));
    }

    public void addPrivateNote(AnnotationScope scope, String text) {
        notesByScope.computeIfAbsent(scope, s -> new ArrayList<>()).add(new PrivateNote(scope, requireText(text)));
    }

    private String requireText(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
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
}
