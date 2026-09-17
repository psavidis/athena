package com.athena.web.reviewrecorder;

/**
 * Request body to start a Review Recording (ticket #203) from the
 * caller's currently selected PR or Diff. {@code disclosureAcknowledged}
 * must be {@code true} — the caller must have shown the user the capture
 * disclosure ({@link ReviewRecordingController#captureDisclosure()})
 * before this request is made. {@code audioEnabled} (ticket #208) is the
 * developer's own opt-in choice — defaults to {@code false} when the
 * caller omits the field, matching the disclosure's own "only if you
 * enable it" framing.
 */
public record StartReviewRecordingRequest(String displayName, boolean disclosureAcknowledged, boolean audioEnabled) {

    public StartReviewRecordingRequest(String displayName, boolean disclosureAcknowledged) {
        this(displayName, disclosureAcknowledged, false);
    }
}
