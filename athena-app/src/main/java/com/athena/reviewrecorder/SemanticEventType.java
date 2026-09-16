package com.athena.reviewrecorder;

/**
 * The kinds of semantic event a Review Recording captures (ticket #204):
 * canvas navigation, semantic zoom changes, entities inspected, code/diff
 * areas viewed, and comments created. Extracting "moments"
 * (question/decision/concern) from these is a later ticket's concern, not
 * this enum's.
 */
public enum SemanticEventType {
    CANVAS_NAVIGATION,
    SEMANTIC_ZOOM_CHANGE,
    ENTITY_INSPECTED,
    DIFF_AREA_VIEWED,
    COMMENT_CREATED
}
