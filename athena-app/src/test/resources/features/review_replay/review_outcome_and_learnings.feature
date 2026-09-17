Feature: Review Replay — outcome and derived learnings
  A developer opens a Replay's concise end-of-review outcome — what was
  learned, decided, left unresolved, and what actions remain — derived
  from its confirmed moments (tickets #205, #206), distinct from a flat
  list of events. Every derived item is traceable back to the moment it
  came from. Not a new canonical record: this is a view over the same
  moments Replay already resolves (ticket #210).

  Scenario: A developer sees what was learned
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has tagged the current moment as an insight
    And "Petros" has confirmed that moment
    And "Petros" has stopped the recording
    When a developer opens the Replay's outcome
    Then the outcome's learned section has 1 item about the "OrderService" entity

  Scenario: A developer sees what was decided
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "PaymentGateway" entity
    And "Petros" has tagged the current moment as a decision
    And "Petros" has confirmed that moment
    And "Petros" has stopped the recording
    When a developer opens the Replay's outcome
    Then the outcome's decided section has 1 item about the "PaymentGateway" entity

  Scenario: A developer sees what's left unresolved
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has tagged the current moment as a question
    And "Petros" has confirmed that moment
    And "Petros" has stopped the recording
    When a developer opens the Replay's outcome
    Then the outcome's unresolved section has 1 item about the "OrderService" entity

  Scenario: A developer sees what actions remain
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has tagged the current moment as an action
    And "Petros" has confirmed that moment
    And "Petros" has stopped the recording
    When a developer opens the Replay's outcome
    Then the outcome's actions section has 1 item about the "OrderService" entity

  Scenario: A moment that was never confirmed does not appear in the outcome
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has tagged the current moment as an insight
    And "Petros" has stopped the recording
    When a developer opens the Replay's outcome
    Then the outcome's learned section is empty

  Scenario: A rejected moment does not appear in the outcome
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has tagged the current moment as an insight
    And "Petros" has rejected that moment
    And "Petros" has stopped the recording
    When a developer opens the Replay's outcome
    Then the outcome's learned section is empty

  Scenario: A Replay with no confirmed moments of a kind shows that section empty
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" has stopped the recording
    When a developer opens the Replay's outcome
    Then the outcome's decided section is empty
