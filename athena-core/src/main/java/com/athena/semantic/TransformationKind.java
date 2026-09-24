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
    CHANGE_FIELD_ANNOTATIONS,
    ADD_ENUM_CONSTANT,
    REMOVE_ENUM_CONSTANT,
    ADD_ANNOTATION_ELEMENT,
    REMOVE_ANNOTATION_ELEMENT,
    CHANGE_ANNOTATION_ELEMENT_DEFAULT,
    CHANGE_FIELD_TYPE,
    CHANGE_PARAMETER_ANNOTATIONS,
    CHANGE_METHOD_ANNOTATIONS,
    CHANGE_CONTROL_FLOW,
    MODIFY_METHOD_BODY,
    // A field/method several existing classes lost to a newly added common base class that
    // gained it (ticket #290). Involved: "Base#member" first, then each source "Sub#member".
    PULL_UP_FIELD,
    PULL_UP_SYMBOL,
    // A declaration's visibility or modifier keywords changed (ticket #313). Involved:
    // "Type#member" (or "Type" for a class), then the delta, e.g. "+final".
    CHANGE_MODIFIERS,
    // A field's initializer changed (ticket #316). Involved: "Type#field", then a summary,
    // e.g. "+HALF_DAY" or "100 -> 200".
    CHANGE_FIELD_VALUE,
    // A build dependency added, removed or changed (ticket #340; the Maven plugin). Involved:
    // "module#groupId:artifactId" (" [managed]" for dependencyManagement), then for a change
    // its delta, e.g. "scope test -> compile".
    ADD_DEPENDENCY,
    REMOVE_DEPENDENCY,
    CHANGE_DEPENDENCY
}
