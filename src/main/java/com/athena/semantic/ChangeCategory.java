package com.athena.semantic;

/**
 * The spec's semantic taxonomy for a Change (epic #4 §9), derived from a
 * Change's {@link TransformationKind}. Classification is advisory — the
 * human reviewer remains authoritative — but it is what review coverage
 * (§20) is reported against.
 */
public enum ChangeCategory {
    MECHANICAL,
    STRUCTURAL,
    BEHAVIORAL,
    UNKNOWN;

    static ChangeCategory of(TransformationKind kind) {
        return switch (kind) {
            case MECHANICAL_REPLACEMENT, FORMATTING_ONLY -> MECHANICAL;
            case RENAME_SYMBOL, MOVE_SYMBOL, ADD_SYMBOL, REMOVE_SYMBOL,
                 CHANGE_METHOD_SIGNATURE, EXTRACT_METHOD -> STRUCTURAL;
        };
    }
}
