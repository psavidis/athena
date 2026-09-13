package com.athena.semantic;

import java.util.List;

/**
 * Every {@link Change} whose touched files share a top-level module (the
 * first path segment under a Maven/Gradle multi-module layout, e.g.
 * "crowdness-live/src/main/java/..." -> "crowdness-live") — a coarser unit
 * than {@link Change#enclosingType()}: a module groups many classes across
 * many files, which is the right scope for "why was this module touched?"
 * (a reason can span several classes, unlike a per-class grouping).
 */
public final class ModuleGroup {

    private final String moduleName;
    private final List<Change> changes;

    ModuleGroup(String moduleName, List<Change> changes) {
        this.moduleName = moduleName;
        this.changes = List.copyOf(changes);
    }

    public String moduleName() {
        return moduleName;
    }

    public List<Change> changes() {
        return changes;
    }
}
