package com.athena.web.response;

/** The outcome of a confirmed review submission, serialized for the frontend (ticket #76). */
public record SubmissionResponse(boolean fullySynced, int syncedCommentCount, int failedCommentCount) {
}
