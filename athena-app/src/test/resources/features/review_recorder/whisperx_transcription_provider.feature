Feature: WhisperX-backed transcription for a Review Recording
  Athena produces a real, speaker-attributed transcript for a Review
  Recording's captured audio (ticket #251), replacing the always-empty
  NoOpTranscriptionProvider (ticket #208) with a self-hosted WhisperX +
  pyannote.audio pipeline (ticket #250's decision). Two recording modes
  are supported: in-person, where every participant speaks into one
  shared microphone and speakers are told apart by voice (diarization);
  and remote/call-based, where each participant's own client captures
  only their own mic and the resulting per-participant transcripts are
  merged into one conversation, in the order they were actually spoken.

  Scenario: An in-person recording through a shared microphone produces a speaker-attributed transcript
    Given a Review Recording captured through one shared microphone with two participants speaking
    When the recording's transcript is produced
    Then the transcript includes what each participant said
    And each spoken segment is attributed to a distinct speaker

  Scenario: A recording with only one speaker still produces a transcript
    Given a Review Recording captured through one shared microphone with a single participant speaking
    When the recording's transcript is produced
    Then the transcript includes what that participant said

  Scenario: Two remote participants' separately-captured audio merges into one conversation
    Given "Alice" has captured her own microphone locally during a remote recording
    And "Bob" has captured his own microphone locally during the same remote recording
    When the recording's transcript is produced
    Then the transcript includes what "Alice" said, attributed to "Alice"
    And the transcript includes what "Bob" said, attributed to "Bob"
    And the transcript is ordered by when each segment was actually spoken, not by which participant uploaded first

  Scenario: A remote participant's audio never arrives, but the recording still produces a usable transcript
    Given "Alice" has captured her own microphone locally during a remote recording
    And "Bob"'s captured audio never reaches the recording
    When the recording's transcript is produced
    Then the transcript includes what "Alice" said, attributed to "Alice"
    And no error occurs

  Scenario: Transcription is unavailable, and the recording is not corrupted
    Given a Review Recording with audio capture enabled
    And the transcription pipeline is unavailable
    When the recording's transcript is produced
    Then the transcript is empty
    And the underlying Review Recording remains intact
    And no error occurs
