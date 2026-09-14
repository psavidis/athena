package com.athena.web.reviewui;

import com.athena.reviewui.Comment;

import java.time.Instant;

/** One comment on a Semantic Canvas item (ticket #134), as sent to the frontend. */
public record CanvasCommentResponse(String id, String author, Instant postedAt, String text) {

    static CanvasCommentResponse of(Comment comment) {
        return new CanvasCommentResponse(comment.id(), comment.author(), comment.postedAt(), comment.text());
    }
}
