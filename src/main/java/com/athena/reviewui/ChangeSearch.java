package com.athena.reviewui;

import com.athena.semantic.Change;
import com.athena.semantic.ChangeEvidence;

import java.util.List;
import java.util.Locale;

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
        String needle = query.toLowerCase(Locale.ROOT);
        return changes.stream()
                .filter(change -> matches(change, board, needle))
                .toList();
    }

    private static boolean matches(Change change, AnnotationBoard board, String needle) {
        if (change.title().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        ChangeEvidence evidence = ChangeEvidence.of(change);
        if (evidence.files().stream().anyMatch(file -> file.toLowerCase(Locale.ROOT).contains(needle))) {
            return true;
        }
        if (evidence.symbols().stream().anyMatch(symbol -> symbol.toLowerCase(Locale.ROOT).contains(needle))) {
            return true;
        }
        return board.commentsAt(AnnotationScope.change(change)).stream()
                .anyMatch(comment -> comment.text().toLowerCase(Locale.ROOT).contains(needle));
    }
}
