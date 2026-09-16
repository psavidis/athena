package com.athena.web.reviewrecorder;

/**
 * Request body to capture a semantic event on an active Review Recording
 * (ticket #204). {@code type} names a {@code SemanticEventType} value by
 * name (e.g. {@code "CANVAS_NAVIGATION"}) — a plain external/wire string
 * rather than the enum itself, per CODE_STYLE.md &sect;D.1 (external
 * DTOs stay serializable and don't leak internal types).
 */
public record CaptureSemanticEventRequest(String type, String reference) {
}
