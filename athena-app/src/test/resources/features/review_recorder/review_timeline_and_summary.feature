Feature: Review timeline and session summary
  A developer sees their active or completed review as a semantic
  timeline of moments, not a raw event log, and gets a concise summary
  when they stop recording (ticket #206). A human-confirmation step
  guards every tagged moment — even an explicit tag — before it becomes
  durable, so a mis-tap can still be corrected.

  Scenario: A tagged moment starts out pending confirmation
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    When "Petros" tags the current moment as a question
    Then the recording's timeline shows a pending question referencing the "OrderService" entity

  Scenario: Confirming a pending moment makes it durable
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has tagged the current moment as a question
    When "Petros" confirms that moment
    Then the recording's timeline shows a confirmed question referencing the "OrderService" entity

  Scenario: Editing a pending moment changes its kind before confirming
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has tagged the current moment as a question
    When "Petros" edits that moment to a concern
    And "Petros" confirms that moment
    Then the recording's timeline shows a confirmed concern referencing the "OrderService" entity

  Scenario: Rejecting a pending moment discards it
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has tagged the current moment as a question
    When "Petros" rejects that moment
    Then the recording's timeline does not show a question referencing the "OrderService" entity

  Scenario: Confirming an already-confirmed moment is rejected
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has tagged the current moment as a question
    And "Petros" has confirmed that moment
    When "Petros" attempts to confirm that moment again
    Then the review recording request is rejected as invalid

  Scenario: Stopping a recording summarizes confirmed moment counts by kind
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has tagged the current moment as a question
    And "Petros" has confirmed that moment
    And "Petros" navigates the canvas to the "PaymentValidator" component
    And "Petros" has tagged the current moment as a decision
    And "Petros" has confirmed that moment
    When "Petros" stops the recording
    Then the stop summary shows 1 question and 1 decision
    And the stop summary shows the recording's duration

  Scenario: An unconfirmed moment is excluded from the stop summary's counts
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has tagged the current moment as a question
    When "Petros" stops the recording
    Then the stop summary shows 0 questions
