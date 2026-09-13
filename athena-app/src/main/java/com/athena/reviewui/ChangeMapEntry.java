package com.athena.reviewui;

import com.athena.semantic.Change;
import com.athena.semantic.ChangeCategory;
import com.athena.semantic.ReviewState;

/**
 * One row of the Change Map: enough about a {@link Change} to identify it
 * without opening it — its category, a human-readable description of the
 * transformation, and its current review state (epic #5 §14).
 */
public final class ChangeMapEntry {

    private final Change change;
    private final ChangeCategory category;
    private final ReviewState reviewState;

    ChangeMapEntry(Change change, ChangeCategory category, ReviewState reviewState) {
        this.change = change;
        this.category = category;
        this.reviewState = reviewState;
    }

    /** The underlying Change this entry describes. */
    public Change change() {
        return change;
    }

    public ChangeCategory category() {
        return category;
    }

    /** A human-readable description of the transformation (the Change's own title). */
    public String description() {
        return change.title();
    }

    public ReviewState reviewState() {
        return reviewState;
    }
}
