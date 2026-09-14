package com.athena.semantic;

import java.util.List;

/**
 * One module's spatial "territory" on the Semantic Canvas (ticket #129): a
 * module the PR touches, or an unmodified module a touched module actually
 * depends on. {@link #status()} is what the canvas paints — new/touched/idle
 * — and {@link #changes()} is empty for an idle territory (it was never
 * grouped by {@link ModuleGrouper} because nothing in it changed). Exposes
 * the underlying {@link Change}s rather than an encoded key, since key
 * encoding ({@code com.athena.web.ChangeKey}) is a web-layer concern this
 * core class doesn't depend on.
 */
public final class ModuleTerritory {

    private final String moduleName;
    private final ModuleStatus status;
    private final int fileCount;
    private final String statusSummary;
    private final TechStack techStack;
    private final List<Change> changes;

    ModuleTerritory(String moduleName, ModuleStatus status, int fileCount, String statusSummary,
                     TechStack techStack, List<Change> changes) {
        this.moduleName = moduleName;
        this.status = status;
        this.fileCount = fileCount;
        this.statusSummary = statusSummary;
        this.techStack = techStack;
        this.changes = List.copyOf(changes);
    }

    public String moduleName() {
        return moduleName;
    }

    public ModuleStatus status() {
        return status;
    }

    public int fileCount() {
        return fileCount;
    }

    /** A one-line human summary, e.g. "17 files · one new query" — never the bare word "untouched". */
    public String statusSummary() {
        return statusSummary;
    }

    public TechStack techStack() {
        return techStack;
    }

    public List<Change> changes() {
        return changes;
    }
}
