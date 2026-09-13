package com.athena.plugin.java;

/**
 * Records that some reference within the source tree (e.g. a call to a type
 * not on the configured classpath) could not be resolved during model
 * building. The model still lists everything else it could determine
 * (partial/best-effort resolution) — see epic #4's requirement that a
 * resolution failure must be reported per-symbol rather than aborting the
 * whole model build.
 */
public final class ResolutionFailure {

    private final String compilationUnitPath;
    private final String description;

    ResolutionFailure(String compilationUnitPath, String description) {
        this.compilationUnitPath = compilationUnitPath;
        this.description = description;
    }

    public String compilationUnitPath() {
        return compilationUnitPath;
    }

    public String description() {
        return description;
    }

    @Override
    public String toString() {
        return compilationUnitPath + ": " + description;
    }
}
