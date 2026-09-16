package com.athena.web.reviewrecorder;

/**
 * Request body to tag the current moment on an active Review Recording
 * (ticket #205). {@code kind} names a {@code MomentKind} value by name
 * (e.g. {@code "QUESTION"}) — a plain external/wire string rather than
 * the enum itself, per CODE_STYLE.md &sect;D.1.
 */
public record TagMomentRequest(String kind) {
}
