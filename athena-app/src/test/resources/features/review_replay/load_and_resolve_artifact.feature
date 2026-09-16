Feature: Loading and resolving a recorded review artifact for Replay
  A developer reopens a previously recorded and persisted Review Recording
  artifact (ticket #207) as a Replay: its entity/Change references are
  resolved against the current codebase so the developer sees what each
  reference resolves to now, rather than raw opaque ids (ticket #210).
  This ticket defines the Replay-facing contract only — no timeline UI
  and no transcript handling yet (later tickets).

  Scenario: A developer opens a Replay of a persisted artifact
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has tagged the current moment as a question
    And "Petros" has confirmed that moment
    And "Petros" has stopped the recording
    When a developer opens a Replay of the persisted artifact
    Then the Replay reports repository "acme/widgets", PR 42, and the recording's commit
    And the Replay's reference to the "OrderService" entity is resolved

  Scenario: An entity renamed since the recording is shown as unresolved
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has tagged the current moment as a question
    And "Petros" has confirmed that moment
    And "Petros" has stopped the recording
    And the "OrderService" entity has since been renamed and no longer resolves
    When a developer opens a Replay of the persisted artifact
    Then the Replay's reference to the "OrderService" entity is shown as unresolved
    And the Replay still reports repository "acme/widgets", PR 42, and the recording's commit

  Scenario: Opening a Replay for an unknown recording id is rejected
    When a developer attempts to open a Replay of artifact "does-not-exist"
    Then the Replay request is rejected as invalid
