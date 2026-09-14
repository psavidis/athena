package com.athena.web.reviewui;

/** Request body for posting or editing a Semantic Canvas item's comment (ticket #134). */
public record CanvasCommentRequest(String text) {
}
