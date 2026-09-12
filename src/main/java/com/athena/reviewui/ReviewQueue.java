package com.athena.reviewui;

import com.athena.semantic.Change;
import com.athena.semantic.ChangeCategory;

import java.util.Comparator;
import java.util.List;

/**
 * A deterministic suggested order to look at a PR's Changes in — never a
 * gate. A reviewer remains free to open any Change directly via the Change
 * Map regardless of this ordering (epic #5 §17).
 *
 * <p>MVP heuristic: {@link ChangeCategory#reviewPriority()} (behavioral/
 * structural/unknown ahead of purely mechanical), since those are more
 * likely to need a reviewer's full attention than a mechanical rename/
 * replacement. No AI-driven or confidence-scored ordering — see epic #5
 * #39's explicit Limitations of Scope.
 */
public final class ReviewQueue {

    private final List<Change> orderedChanges;

    private ReviewQueue(List<Change> orderedChanges) {
        this.orderedChanges = orderedChanges;
    }

    public static ReviewQueue of(List<Change> changes) {
        List<Change> ordered = changes.stream()
                .sorted(Comparator.comparingInt(change -> ChangeCategory.of(change.kind()).reviewPriority()))
                .toList();
        return new ReviewQueue(ordered);
    }

    /** The suggested order to review Changes in. Purely advisory — never a gate on navigation. */
    public List<Change> orderedChanges() {
        return orderedChanges;
    }
}
