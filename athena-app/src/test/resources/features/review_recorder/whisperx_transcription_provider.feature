Feature: WhisperX-backed transcription for a Review Recording
  Athena produces a real, speaker-attributed transcript for a Review
  Recording's captured audio (ticket #251), replacing the always-empty
  NoOpTranscriptionProvider (ticket #208) with a self-hosted WhisperX +
  pyannote.audio pipeline (ticket #250's decision). This ticket covers
  the in-person mode, where every participant speaks into one shared
  microphone and speakers are told apart by voice (diarization), and the
  algorithm that merges already-transcribed per-participant streams into
  one chronological conversation for the remote mode — wiring that
  algorithm to real multi-participant capture is ticket #252.

  Scenario: An in-person recording through a shared microphone produces a speaker-attributed transcript
    Given a Review Recording captured through one shared microphone with two participants speaking
    When the recording's transcript is produced
    Then the transcript includes what each participant said
    And each spoken segment is attributed to a distinct speaker

  Scenario: A recording with only one speaker still produces a transcript
    Given a Review Recording captured through one shared microphone with a single participant speaking
    When the recording's transcript is produced
    Then the transcript includes what that participant said

  Scenario: Transcription is unavailable, and the recording is not corrupted
    Given a Review Recording with audio capture enabled
    And the transcription pipeline is unavailable
    When the recording's transcript is produced
    Then the transcript is empty
    And the underlying Review Recording remains intact
    And no error occurs
