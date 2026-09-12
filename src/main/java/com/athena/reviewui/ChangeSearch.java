package com.athena.reviewui;

import com.athena.semantic.Change;
import com.athena.semantic.ChangeEvidence;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Searches Changes by text across the MVP-prioritized subset (epic #5
 * §42): a Change's own description, the files it touches, the symbols it
 * involves, and any comments attached to it. Case-insensitive substring
 * match — no ranking/relevance scoring for MVP.
 */
public final class ChangeSearch {

    private ChangeSearch() {
    }

    public static List<Change> search(List<Change> changes, AnnotationBoard board, String query) {
        Objects.requireNonNull(changes, "changes");
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(query, "query");
        if (query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return changes.stream()
                .filter(change -> matches(change, board, needle))
                .toList();
    }

    private static boolean matches(Change change, AnnotationBoard board, String needle) {
        ChangeEvidence evidence = ChangeEvidence.of(change);
        List<Comment> comments = board.commentsAt(AnnotationScope.change(change));
        return Stream.concat(
                Stream.concat(Stream.of(change.title()), evidence.files().stream()),
                Stream.concat(evidence.symbols().stream(), comments.stream().map(Comment::text))
        ).anyMatch(field -> containsIgnoreCase(field, needle));
    }

    private static boolean containsIgnoreCase(String field, String lowercaseNeedle) {
        return field.toLowerCase(Locale.ROOT).contains(lowercaseNeedle);
    }
}
