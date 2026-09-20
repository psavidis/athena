Feature: Transcript-to-entity alignment and speaker attribution
  Athena aligns spoken transcript segments to whichever semantic
  entity/change was in focus in the Review Recording at the segment's
  timestamp (ticket #209), and carries speaker attribution through when
  the transcription provider supplies one. Alignment is timestamp +
  entity-in-focus based; perfect alignment is explicitly not required
  (see the ticket's "Limitations of Scope"). Today, the only
  TranscriptionProvider wired up is a no-op (ticket #208) that never
  produces a transcript, so shipping #209 "as best-effort" means: the
  alignment logic is real and tested against a transcript shape, but in
  the running system it always has nothing to align, which must be
  handled gracefully rather than as an error.

  Scenario: A transcript segment aligns to the entity in focus when it was spoken
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets", with a transcript to align
    And "Petros" inspects the "PaymentProcessor" entity
    And some time passes during the recording
    And a transcript segment "Could this execute twice?" spoken by "Alice" arrives after that
    When Athena aligns the recording's transcript to its semantic events
    Then the aligned segment references the "PaymentProcessor" entity
    And the aligned segment is attributed to speaker "Alice"

  Scenario: A transcript segment aligns to the most recent entity in focus, not a later one
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets", with a transcript to align
    And "Petros" inspects the "PaymentProcessor" entity
    And some time passes during the recording
    And a transcript segment "Could this execute twice?" spoken by "Alice" arrives after that
    And some time passes during the recording
    And "Petros" inspects the "RetryWorker" entity
    When Athena aligns the recording's transcript to its semantic events
    Then the aligned segment references the "PaymentProcessor" entity

  Scenario: A transcript segment spoken before any entity was in focus is left unaligned
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets", with a transcript to align
    And a transcript segment "Let's get started" spoken by "Bob" arrives after that
    When Athena aligns the recording's transcript to its semantic events
    Then the aligned segment has no referenced entity

  Scenario: A transcript segment with no speaker attribution from the provider
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets", with a transcript to align
    And "Petros" inspects the "PaymentProcessor" entity
    And some time passes during the recording
    And a transcript segment "Could this execute twice?" with no known speaker arrives after that
    When Athena aligns the recording's transcript to its semantic events
    Then the aligned segment references the "PaymentProcessor" entity
    And the aligned segment has no attributed speaker

  Scenario: No transcript is available to align (today's real-world case)
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets", with a transcript to align
    And "Petros" inspects the "PaymentProcessor" entity
    And no transcript was produced for the recording
    When Athena aligns the recording's transcript to its semantic events
    Then no aligned segments are produced
    And no error occurs

  Scenario: An aligned segment is presented as raw data, never as an explicit decision
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets", with a transcript to align
    And "Petros" inspects the "PaymentProcessor" entity
    And some time passes during the recording
    And a transcript segment "Let's use the idempotency key" spoken by "Bob" arrives after that
    When Athena aligns the recording's transcript to its semantic events
    Then the aligned segment references the "PaymentProcessor" entity
    And the aligned segment is marked as raw transcript, not as a confirmed decision
