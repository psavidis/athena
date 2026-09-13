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
    FORMATTING_ONLY,
    RENAME_CLASS,
    MOVE_CLASS,
    ADD_CLASS,
    REMOVE_CLASS,
    RENAME_FIELD,
    MOVE_FIELD,
    ADD_FIELD,
    REMOVE_FIELD,
    ADD_CONSTRUCTOR_PARAMETER,
    CHANGE_FIELD_ANNOTATIONS
}
