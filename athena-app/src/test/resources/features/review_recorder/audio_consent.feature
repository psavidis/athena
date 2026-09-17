Feature: Review Recorder — optional audio capture consent
  A developer opts in to audio capture when starting a Review Recording
  (ticket #208), so spoken discussion isn't lost even when it's never
  typed as a comment. Enabling audio only records the developer's
  intent — no real transcription provider exists yet (flagged in this
  ticket's own scope), so no audio is actually captured, stored, or
  transmitted; a later ticket wires up a real provider once one is
  chosen.

  Scenario: A developer starts a Review Recording with audio capture enabled
    Given a developer has PR 42 in "acme/widgets" selected
    When the developer starts a Review Recording as "Petros" with audio enabled, having acknowledged the disclosure
    Then the recording has audio capture enabled

  Scenario: A developer starts a Review Recording without enabling audio
    Given a developer has PR 42 in "acme/widgets" selected
    When the developer starts a Review Recording as "Petros", having acknowledged the disclosure
    Then the recording does not have audio capture enabled

  Scenario: A reopened artifact still shows whether audio capture was enabled
    Given a developer has PR 42 in "acme/widgets" selected
    And "Petros" has started a Review Recording for PR 42 in "acme/widgets" with audio enabled
    And "Petros" has stopped the recording
    When a developer reopens the persisted artifact
    Then the reopened artifact shows audio capture as enabled
