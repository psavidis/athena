package com.athena.web.reviewrecorder;

/** Returned when starting a Review Recording (ticket #203): its id plus its current snapshot. */
public record ReviewRecordingStartResponse(String recordingId, ReviewRecordingSnapshot snapshot) {
}
