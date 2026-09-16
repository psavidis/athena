package com.athena.web.livesession;

/**
 * Request body to add a collaborative comment during a Live Code Review
 * Session (ticket #158). {@code canvasItemId} is the same opaque canvas
 * item id {@code CanvasCommentController} already uses; {@code null} scopes
 * the comment to the whole review instead of one semantic entity.
 */
public record LiveCommentRequest(String participantId, String canvasItemId, String text) {
}
