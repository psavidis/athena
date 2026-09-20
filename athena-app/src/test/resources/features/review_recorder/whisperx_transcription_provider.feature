Feature: WhisperX-backed transcription for a Review Recording
  Athena produces a real, speaker-attributed transcript from a Review
  Recording's audio (ticket #251), replacing the always-empty
  NoOpTranscriptionProvider (ticket #208) with a self-hosted WhisperX +
  pyannote.audio pipeline (ticket #250's decision), diarizing a single
  mixed mono audio recording — the in-person, single-shared-microphone
  mode. This ticket transcribes an audio file already captured by some
  other means (ticket #253 implements the actual microphone capture);
  it does not capture audio itself.

  Scenario: An audio recording with two speakers produces a speaker-attributed transcript
    Given an audio recording of two participants speaking, one after the other
    When the recording's transcript is produced
    Then the transcript includes what each participant said
    And each spoken segment is attributed to a distinct speaker

  Scenario: An audio recording with only one speaker still produces a transcript
    Given an audio recording of a single participant speaking
    When the recording's transcript is produced
    Then the transcript includes what that participant said

  Scenario: Transcription is unavailable, and the recording is not corrupted
    Given a Review Recording with audio capture enabled
    And the transcription pipeline is unavailable
    When the recording's transcript is produced
    Then the transcript is empty
    And the underlying Review Recording remains intact
