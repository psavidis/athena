package com.athena.web.reviewrecorder;

/**
 * Request body to start a Review Recording (ticket #203) from the
 * caller's currently selected PR or Diff. {@code disclosureAcknowledged}
 * must be {@code true} — the caller must have shown the user the capture
 * disclosure ({@link ReviewRecordingController#captureDisclosure()})
 * before this request is made.
 */
public record StartReviewRecordingRequest(String displayName, boolean disclosureAcknowledged) {
}
