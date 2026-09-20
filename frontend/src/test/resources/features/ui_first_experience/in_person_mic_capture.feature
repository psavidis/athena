Feature: In-person single-microphone audio capture
  When a developer enables audio capture for a Review Recording (ticket
  #208's consent flag), and the recording is in-person around one shared
  microphone, Athena actually captures that microphone's audio for the
  duration of the recording and uploads it once the recording stops, so
  #251's WhisperX-backed transcription has a real file to diarize and
  transcribe (ticket #253). This is the in-person counterpart to #252's
  remote-mode capture; the two share the same backend upload endpoint,
  but only this ticket triggers it from a real captured microphone
  rather than a per-participant remote-call stream.

  Microphone access itself (browser permission, hardware presence) is a
  genuine external boundary this suite cannot exercise for real (no
  physical microphone or browser permission prompt in a test run), so
  these scenarios drive a fake capture device standing in for it —
  never a mock of Athena's own code, only of the one thing outside this
  codebase's control.

  Scenario: Starting a recording with audio enabled begins capturing the shared microphone
    Given a developer is viewing a review with the Review Recording control
    And the shared microphone is available
    When the developer starts the recording with audio enabled, acknowledging the disclosure
    Then microphone capture begins

  Scenario: Starting a recording without audio enabled never touches the microphone
    Given a developer is viewing a review with the Review Recording control
    And the shared microphone is available
    When the developer starts the recording, acknowledging the disclosure
    Then microphone capture never begins

  Scenario: Stopping a recording uploads the captured audio
    Given a developer has an active Review Recording with audio enabled and the microphone capturing
    When the developer stops the recording
    Then the captured audio is uploaded for that recording
    And the recording indicator is no longer shown

  Scenario: Microphone permission is denied, but the recording still starts
    Given a developer is viewing a review with the Review Recording control
    And the shared microphone will deny permission
    When the developer starts the recording with audio enabled, acknowledging the disclosure
    Then the recording indicator is shown with elapsed time and participant count
    And no audio is uploaded when the recording stops

  Scenario: The browser does not support microphone capture, but the recording still starts
    Given a developer is viewing a review with the Review Recording control
    And this browser does not support microphone capture
    When the developer starts the recording with audio enabled, acknowledging the disclosure
    Then the recording indicator is shown with elapsed time and participant count
    And no audio is uploaded when the recording stops
