package com.athena.web.github;

/** An existing review comment on a Pull Request, serialized for the frontend (ticket #192). */
public record ReviewCommentResponse(String author, String body, String path) {
}
