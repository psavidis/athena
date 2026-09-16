package com.athena.web.reviewrecorder;

/** Request body to join an active Review Recording (ticket #203) as a participant. */
public record JoinReviewRecordingRequest(String displayName) {
}
