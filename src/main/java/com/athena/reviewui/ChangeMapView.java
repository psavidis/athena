package com.athena.reviewui;

import com.athena.semantic.Change;
import com.athena.semantic.ChangeCategory;
import com.athena.semantic.ReviewStateStore;

import java.util.List;

/**
 * The Change Map: the reviewer's default entry point into a PR, listing
 * every detected Change instead of a file tree (epic #5 §14). Each entry
 * carries enough to identify the Change without opening it.
 *
 * <p>File-tree fallback and mixed degraded-analysis rendering (§45) are a
 * separate concern layered on top of this view once the Semantic Change
 * Engine's graceful-degradation fallback chain (epic #4 ticket #22) is
 * available — this class covers the fully-analyzed case.
 */
public final class ChangeMapView {

    private final List<ChangeMapEntry> entries;

    private ChangeMapView(List<ChangeMapEntry> entries) {
        this.entries = entries;
    }

    public static ChangeMapView of(List<Change> changes, ReviewStateStore reviewStateStore) {
        List<ChangeMapEntry> entries = changes.stream()
                .map(change -> new ChangeMapEntry(change,
                        ChangeCategory.of(change.kind()),
                        reviewStateStore.stateOf(change)))
                .toList();
        return new ChangeMapView(entries);
    }

    public List<ChangeMapEntry> entries() {
        return entries;
    }
}
