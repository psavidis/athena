package com.athena.web.reviewrecorder;

import com.athena.reviewrecorder.TranscriptionProvider;

/**
 * Request to upload one participant's already-captured audio for a
 * remote/call-based Review Recording (ticket #252). {@code
 * transcriptionProvider} is a test-only seam — not something a real
 * client ever sends over the wire — that lets a test substitute a
 * fixture transcript instead of shelling out to a real WhisperX process
 * per scenario; the real HTTP entry point ({@link
 * ReviewRecordingController#uploadAudio(String, org.springframework.web.multipart.MultipartFile, String)})
 * never constructs one of these directly, only this package-visible
 * constructor does, from the uploaded audio file.
 */
public record UploadRemoteAudioRequest(String participantDisplayName, TranscriptionProvider transcriptionProvider) {
}
