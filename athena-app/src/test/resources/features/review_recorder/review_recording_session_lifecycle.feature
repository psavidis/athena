Feature: Review Recording session lifecycle
  A developer explicitly starts and stops a Review Recording around a
  review they already have open, so a session is only captured when they
  choose to, with a clear on/off signal (ticket #203). This ticket is the
  session lifecycle shell only — no audio, transcription, or semantic
  event capture yet; those build on this foundation in later tickets.

  Scenario: A developer sees the capture disclosure before starting a recording
    Given a developer has PR 42 in "acme/widgets" selected
    When the developer opens the Start Review Recording action
    Then the developer is shown a disclosure of what will be captured

  Scenario: A developer starts a Review Recording after acknowledging the disclosure
    Given a developer has PR 42 in "acme/widgets" selected
    When the developer starts a Review Recording as "Petros", having acknowledged the disclosure
    Then the recording exists
    And the recording is active
    And "Petros" appears as a participant in the recording

  Scenario: Starting a recording without acknowledging the disclosure is rejected
    Given a developer has PR 42 in "acme/widgets" selected
    When the developer attempts to start a Review Recording as "Petros", without acknowledging the disclosure
    Then the review recording request is rejected as invalid

  Scenario: Starting a recording with no review selected is rejected
    Given a developer has no PR or Diff selected
    When the developer attempts to start a Review Recording as "Petros", having acknowledged the disclosure
    Then the review recording request is rejected as invalid

  Scenario: A developer starts a recording from a standalone Diff, with no GitHub PR at all
    Given a developer has a standalone Diff selected, with no GitHub PR
    When the developer starts a Review Recording as "Petros", having acknowledged the disclosure
    Then the recording exists
    And the recording is active

  Scenario: A second participant joins an active recording
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    When "Maria" joins that recording
    Then "Maria" appears as a participant in the recording
    And "Petros" still appears as a participant in the recording
    And the recording's participant count is 2

  Scenario: The recording shows elapsed time while active
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    When some time passes during the recording
    Then the recording's elapsed time increases

  Scenario: A developer stops an active recording
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    When "Petros" stops the recording
    Then the recording is no longer active

  Scenario: Stopping a recording that is already stopped is rejected
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" has stopped the recording
    When "Petros" attempts to stop the recording again
    Then the review recording request is rejected as invalid

  Scenario: Stopping an unknown recording is rejected
    When a developer attempts to stop recording "does-not-exist"
    Then the review recording request is rejected as invalid
