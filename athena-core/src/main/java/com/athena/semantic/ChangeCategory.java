package com.athena.semantic;

/**
 * The spec's semantic taxonomy for a Change (epic #4 §9), derived from a
 * Change's {@link TransformationKind}. Classification is advisory — the
 * human reviewer remains authoritative — but it is what review coverage
 * (§20) is reported against.
 */
public enum ChangeCategory {
    BEHAVIORAL(0),
    STRUCTURAL(1),
    UNKNOWN(2),
    MECHANICAL(3);

    private final int reviewPriority;

    ChangeCategory(int reviewPriority) {
        this.reviewPriority = reviewPriority;
    }

    /**
     * Lower values suggest a reviewer look here first (epic #5 §17's Review Queue heuristic):
     * behavioral/structural/unknown Changes are more likely to need full attention than a
     * purely mechanical one. Declared on the enum itself, per CODE_STYLE.md's Strategy Enum
     * Pattern, so adding a category can't silently omit a priority the way a separate map could.
     */
    public int reviewPriority() {
        return reviewPriority;
    }

    public static ChangeCategory of(TransformationKind kind) {
        return switch (kind) {
            case MECHANICAL_REPLACEMENT, FORMATTING_ONLY -> MECHANICAL;
            case RENAME_SYMBOL, MOVE_SYMBOL, ADD_SYMBOL, REMOVE_SYMBOL,
                 CHANGE_METHOD_SIGNATURE, EXTRACT_METHOD,
                 RENAME_CLASS, MOVE_CLASS, ADD_CLASS, REMOVE_CLASS,
                 RENAME_FIELD, MOVE_FIELD, ADD_FIELD, REMOVE_FIELD,
                 ADD_CONSTRUCTOR_PARAMETER, CHANGE_FIELD_ANNOTATIONS,
                 ADD_ENUM_CONSTANT, REMOVE_ENUM_CONSTANT,
                 ADD_ANNOTATION_ELEMENT, REMOVE_ANNOTATION_ELEMENT, CHANGE_ANNOTATION_ELEMENT_DEFAULT -> STRUCTURAL;
        };
    }
}
