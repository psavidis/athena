Feature: Transcript-to-entity alignment and speaker attribution
  Athena aligns spoken transcript segments to whichever semantic
  entity/change was in focus in the Review Recording at the segment's
  timestamp (ticket #209), and carries speaker attribution through when
  the transcription provider supplies one. Alignment is timestamp +
  entity-in-focus based; perfect alignment is explicitly not required
  (see the ticket's "Limitations of Scope"). Today, the only
  TranscriptionProvider wired up is a no-op (ticket #208) that never
  produces a transcript, so aligning "as best-effort" means: the
  alignment logic is real and tested against a transcript shape, but in
  the running system it always has nothing to align, which must be
  handled gracefully rather than as an error.

  Scenario: A transcript segment aligns to the entity in focus when it was spoken
    Given a review recording with an entity "PaymentProcessor.retry()" inspected at "10:34:00"
    And a transcript segment "Could this execute twice?" spoken at "10:41:00" by "Alice"
    When Athena aligns the transcript to the recording's semantic events
    Then the segment is aligned to entity "PaymentProcessor.retry()"
    And the segment is attributed to speaker "Alice"

  Scenario: A transcript segment aligns to the most recent entity in focus, not a later one
    Given a review recording with an entity "PaymentProcessor.retry()" inspected at "10:34:00"
    And a review recording with an entity "RetryWorker" inspected at "10:50:00"
    And a transcript segment "Could this execute twice?" spoken at "10:41:00" by "Alice"
    When Athena aligns the transcript to the recording's semantic events
    Then the segment is aligned to entity "PaymentProcessor.retry()"

  Scenario: A transcript segment spoken before any entity was in focus is left unaligned
    Given a review recording with no semantic events yet
    And a transcript segment "Let's get started" spoken at "10:30:00" by "Bob"
    When Athena aligns the transcript to the recording's semantic events
    Then the segment has no aligned entity

  Scenario: A transcript segment with no speaker attribution from the provider
    Given a review recording with an entity "PaymentProcessor.retry()" inspected at "10:34:00"
    And a transcript segment "Could this execute twice?" spoken at "10:41:00" with no known speaker
    When Athena aligns the transcript to the recording's semantic events
    Then the segment is aligned to entity "PaymentProcessor.retry()"
    And the segment has no attributed speaker

  Scenario: No transcript is available to align (today's real-world case)
    Given a review recording with an entity "PaymentProcessor.retry()" inspected at "10:34:00"
    And no transcript was produced for the recording
    When Athena aligns the transcript to the recording's semantic events
    Then no aligned segments are produced
    And no error occurs

  Scenario: An aligned segment is presented as raw data, never as an explicit decision
    Given a review recording with an entity "PaymentProcessor.retry()" inspected at "10:34:00"
    And a transcript segment "Let's use the idempotency key" spoken at "10:47:00" by "Bob"
    When Athena aligns the transcript to the recording's semantic events
    Then the segment is aligned to entity "PaymentProcessor.retry()"
    And the aligned segment is marked as raw transcript, not as a confirmed decision
