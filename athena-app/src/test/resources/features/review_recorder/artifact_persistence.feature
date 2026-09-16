Feature: Review Recording artifact persistence
  A developer's completed review session persists project-locally so
  they or a teammate can reopen it later, correctly associated with its
  repository, PR, and commit (ticket #207). No centralized Athena
  service is required for this core recording capability. This ticket
  produces the artifact contract Review Replay (#164) consumes; it does
  not implement Replay itself.

  Scenario: Stopping a recording persists its artifact
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has tagged the current moment as a question
    And "Petros" has confirmed that moment
    When "Petros" stops the recording
    Then the recording's artifact is persisted
    And the persisted artifact is associated with "acme/widgets", PR 42, and the recording's commit

  Scenario: A developer reopens a persisted artifact
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has tagged the current moment as a question
    And "Petros" has confirmed that moment
    And "Petros" has stopped the recording
    When a developer reopens the persisted artifact
    Then the reopened artifact shows the confirmed question referencing the "OrderService" entity
    And the reopened artifact shows the recording's summary

  Scenario: Reopening an unknown artifact is rejected
    When a developer attempts to reopen artifact "does-not-exist"
    Then the review recording request is rejected as invalid

  Scenario: A persistence failure does not corrupt the in-memory recording
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And persisting artifacts is currently failing
    When "Petros" stops the recording
    Then the recording is no longer active
    And the recording's summary can still be read
