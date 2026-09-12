package com.athena.reviewui;

import com.athena.semantic.Change;
import com.athena.semantic.ChangeCategory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A deterministic suggested order to look at a PR's Changes in — never a
 * gate. A reviewer remains free to open any Change directly via the Change
 * Map regardless of this ordering (epic #5 §17).
 *
 * <p>MVP heuristic: category priority (behavioral/structural/unknown ahead
 * of purely mechanical), since those are more likely to need a reviewer's
 * full attention than a mechanical rename/replacement. No AI-driven or
 * confidence-scored ordering — see epic #5 #39's explicit Limitations of
 * Scope.
 */
public final class ReviewQueue {

    private static final Map<ChangeCategory, Integer> CATEGORY_PRIORITY = Map.of(
            ChangeCategory.BEHAVIORAL, 0,
            ChangeCategory.STRUCTURAL, 1,
            ChangeCategory.UNKNOWN, 2,
            ChangeCategory.MECHANICAL, 3
    );

    private final List<Change> orderedChanges;

    private ReviewQueue(List<Change> orderedChanges) {
        this.orderedChanges = orderedChanges;
    }

    public static ReviewQueue of(List<Change> changes) {
        List<Change> ordered = changes.stream()
                .sorted(Comparator.comparingInt(change -> priorityOf(change)))
                .toList();
        return new ReviewQueue(ordered);
    }

    private static int priorityOf(Change change) {
        return CATEGORY_PRIORITY.get(ChangeCategory.of(change.kind()));
    }

    /** The suggested order to review Changes in. Purely advisory — never a gate on navigation. */
    public List<Change> orderedChanges() {
        return orderedChanges;
    }
}
