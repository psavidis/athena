package com.athena.reviewui;

import com.athena.semantic.Change;
import com.athena.semantic.ChangeCategory;
import com.athena.semantic.ChangeEvidence;
import com.athena.semantic.ReviewState;
import com.athena.semantic.ReviewStateStore;

import java.util.List;

/**
 * Filters the Change Map by the MVP-prioritized dimensions (epic #5 §43):
 * category, review state, and symbol. Module filtering is out of scope —
 * no module concept exists anywhere in the codebase yet to filter by; see
 * ticket #42's own framing that exhaustive §43 coverage isn't required.
 */
public final class ChangeFilter {

    private ChangeFilter() {
    }

    public static List<Change> byCategory(List<Change> changes, ChangeCategory category) {
        return changes.stream().filter(change -> ChangeCategory.of(change.kind()) == category).toList();
    }

    public static List<Change> byReviewState(List<Change> changes, ReviewStateStore store, ReviewState state) {
        return changes.stream().filter(change -> store.stateOf(change) == state).toList();
    }

    public static List<Change> bySymbol(List<Change> changes, String symbolDescription) {
        return changes.stream()
                .filter(change -> ChangeEvidence.of(change).symbols().contains(symbolDescription))
                .toList();
    }
}
