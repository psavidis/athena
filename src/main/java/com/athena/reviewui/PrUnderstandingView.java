package com.athena.reviewui;

import com.athena.semantic.Change;
import com.athena.semantic.ChangeCategory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The high-level PR Understanding View a reviewer sees before inspecting
 * individual Changes: the PR's title and its overall scope broken down by
 * Change category (epic #5 §16).
 */
public final class PrUnderstandingView {

    private final String prTitle;
    private final Map<ChangeCategory, Integer> countByCategory;

    private PrUnderstandingView(String prTitle, Map<ChangeCategory, Integer> countByCategory) {
        this.prTitle = prTitle;
        this.countByCategory = countByCategory;
    }

    public static PrUnderstandingView of(String prTitle, List<Change> changes) {
        Map<ChangeCategory, Integer> counts = new EnumMap<>(ChangeCategory.class);
        for (Change change : changes) {
            ChangeCategory category = ChangeCategory.of(change.kind());
            counts.merge(category, 1, Integer::sum);
        }
        return new PrUnderstandingView(prTitle, counts);
    }

    public String prTitle() {
        return prTitle;
    }

    /** How many Changes fall into the given category (0 if none). */
    public int countFor(ChangeCategory category) {
        return countByCategory.getOrDefault(category, 0);
    }
}
