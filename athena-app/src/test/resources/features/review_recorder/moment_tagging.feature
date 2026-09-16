Feature: Explicit moment tagging during a Review Recording
  A developer explicitly marks a moment in their review as an insight,
  question, concern, decision, action, or verification, so the resulting
  recording highlights what mattered instead of a flat event log (ticket
  #205). Moments are explicit human actions only in this ticket —
  inference from audio/transcript is out of scope until that capability
  exists.

  Scenario: A developer tags the current moment as a question
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    When "Petros" tags the current moment as a question
    Then the recording's tagged moments include a question referencing the "OrderService" entity

  Scenario: A developer tags the current moment as a decision
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" navigates the canvas to the "PaymentValidator" component
    When "Petros" tags the current moment as a decision
    Then the recording's tagged moments include a decision referencing the "PaymentValidator" component

  Scenario: Every moment kind can be tagged
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    When "Petros" tags the current moment as an insight
    And "Petros" tags the current moment as a concern
    And "Petros" tags the current moment as an action
    And "Petros" tags the current moment as a verification
    Then the recording's tagged moments include an insight, a concern, an action, and a verification

  Scenario: Tagging with an unknown moment kind is rejected
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    When "Petros" attempts to tag the current moment with an unknown kind
    Then the review recording request is rejected as invalid

  Scenario: Tagging a moment on a recording that is not active is rejected
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has stopped the recording
    When "Petros" attempts to tag the current moment as a question
    Then the review recording request is rejected as invalid

  Scenario: Tagged moments are listed in the order they were tagged
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    When "Petros" tags the current moment as a question
    And "Petros" navigates the canvas to the "PaymentValidator" component
    And "Petros" tags the current moment as a decision
    Then the recording's tagged moments list the question before the decision
