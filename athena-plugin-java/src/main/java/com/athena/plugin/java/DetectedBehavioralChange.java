package com.athena.plugin.java;

/**
 * A detected condition/control-flow change within a method matched across
 * a base and head revision — see epic #4's narrowly-scoped "basic
 * behavioral modifications" (condition changes, added/removed branches and
 * loops only; method-call and return-expression changes are explicitly out
 * of scope, per the epic's resolved Open Questions).
 */
public final class DetectedBehavioralChange {

    private final String symbolDescription;
    private final String reason;

    DetectedBehavioralChange(String symbolDescription, String reason) {
        this.symbolDescription = symbolDescription;
        this.reason = reason;
    }

    /** "EnclosingType#methodName" of the method this behavioral change was found in. */
    public String symbolDescription() {
        return symbolDescription;
    }

    /** A short human-readable reason, e.g. "condition changed" or "branch added". */
    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return symbolDescription + ": " + reason;
    }
}
