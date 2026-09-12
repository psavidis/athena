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

    /** The Change (if any) among {@code changes} whose evidence touches {@code filePath}. */
    public static Optional<Change> findOwningChange(List<Change> changes, String filePath) {
        return changes.stream()
                .filter(change -> ChangeEvidence.of(change).files().contains(filePath))
                .findFirst();
    }
}
