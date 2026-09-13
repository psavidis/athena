package com.athena.semantic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Semantic (not file-based) review coverage: per-category percentage of
 * Changes accounted for (Reviewed, Skipped, or marked mechanical), and the
 * list of meaningful Changes still unreviewed (epic #4 §20).
 */
public final class ReviewCoverageReport {

    private final Map<ChangeCategory, Integer> percentByCategory;

    private ReviewCoverageReport(Map<ChangeCategory, Integer> percentByCategory) {
        this.percentByCategory = percentByCategory;
    }

    public static ReviewCoverageReport of(List<Change> changes, ReviewStateStore store) {
        Map<ChangeCategory, List<Change>> byCategory = new EnumMap<>(ChangeCategory.class);
        for (Change change : changes) {
            byCategory.computeIfAbsent(ChangeCategory.of(change.kind()), k -> new ArrayList<>()).add(change);
        }

        Map<ChangeCategory, Integer> percentages = new EnumMap<>(ChangeCategory.class);
        for (Map.Entry<ChangeCategory, List<Change>> entry : byCategory.entrySet()) {
            List<Change> categoryChanges = entry.getValue();
            long accountedFor = categoryChanges.stream().filter(store::isAccountedFor).count();
            int percent = categoryChanges.isEmpty() ? 0 : (int) Math.round(100.0 * accountedFor / categoryChanges.size());
            percentages.put(entry.getKey(), percent);
        }

        return new ReviewCoverageReport(percentages);
    }

    /** Percentage (0-100) of Changes in this category that are Reviewed, Skipped, or marked mechanical. */
    public int percentReviewed(ChangeCategory category) {
        return percentByCategory.getOrDefault(category, 0);
    }

    /** Meaningful Changes not yet Reviewed, Skipped, or marked mechanical. */
    public static List<Change> unreviewedChanges(List<Change> changes, ReviewStateStore store) {
        return changes.stream().filter(change -> !store.isAccountedFor(change)).toList();
    }

    /**
     * A human-readable "N of M meaningful Changes reviewed" summary across every category,
     * so callers display this exact wording rather than each reconstructing it themselves.
     */
    public static String summary(List<Change> changes, ReviewStateStore store) {
        int reviewedCount = changes.size() - unreviewedChanges(changes, store).size();
        return reviewedCount + " of " + changes.size() + " meaningful Changes reviewed";
    }
}
