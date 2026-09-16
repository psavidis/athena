package com.athena.web.livesession;

import com.athena.reviewui.Comment;

import java.time.Instant;

/** One collaborative comment posted during a Live Code Review Session (ticket #158), as sent to the frontend. */
public record LiveCommentResponse(String id, String author, Instant postedAt, String text) {

    static LiveCommentResponse of(Comment comment) {
        return new LiveCommentResponse(comment.id(), comment.author(), comment.postedAt(), comment.text());
    }
}
