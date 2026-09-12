package com.athena.reviewui;

import com.athena.semantic.Change;
import com.athena.semantic.ChangeEvidence;

import java.util.List;
import java.util.Optional;

/**
 * Answers "which Change does this file/line belong to" — the upward
 * direction of drill-down navigation (epic #5 §15): from a line in a diff
 * back up to the Change it's evidence for.
 */
public final class ChangeOwnership {

    private ChangeOwnership() {
    }

    /**
     * The Changes among {@code changes} whose evidence touches {@code filePath} — a file can
     * legitimately be evidence for more than one Change (e.g. two independent edits to
     * different symbols in the same file), so this returns every owner rather than picking
     * one arbitrarily.
     */
    public static List<Change> findOwningChanges(List<Change> changes, String filePath) {
        return changes.stream()
                .filter(change -> ChangeEvidence.of(change).files().contains(filePath))
                .toList();
    }

    /**
     * The single Change owning {@code filePath}, when exactly one exists. Empty if no Change
     * touches the file; if more than one does, callers needing that ambiguity resolved should
     * use {@link #findOwningChanges} directly rather than have one silently picked here.
     */
    public static Optional<Change> findOwningChange(List<Change> changes, String filePath) {
        List<Change> owners = findOwningChanges(changes, filePath);
        return owners.size() == 1 ? Optional.of(owners.get(0)) : Optional.empty();
    }
}
