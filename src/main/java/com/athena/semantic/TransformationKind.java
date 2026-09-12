package com.athena.semantic;

/**
 * A category of deterministically-detected structural/mechanical
 * transformation between a base and head revision. See epic #4's MVP scope
 * (§53): every kind here is produced by exact structural matching, never a
 * confidence score.
 */
public enum TransformationKind {
    RENAME_SYMBOL,
    MOVE_SYMBOL,
    ADD_SYMBOL,
    REMOVE_SYMBOL,
    CHANGE_METHOD_SIGNATURE,
    EXTRACT_METHOD,
    MECHANICAL_REPLACEMENT,
    FORMATTING_ONLY
}
