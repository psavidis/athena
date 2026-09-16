Feature: Live Code Review Session — collaborative comments
  Participants in a Live Code Review Session discuss the review as they go
  — a comment one participant adds is visible to every other participant
  in the same session in real time, attached to the semantic entity it was
  posted against rather than only a screen position (ticket #158).

  Scenario: A comment posted by one participant is visible to another
    Given "Petros" has started a Live Code Review Session for PR 42 in "acme/widgets"
    And "Maria" has joined that session
    When "Petros" comments "Should this retry on timeout?" on the "PaymentValidator" component
    Then "Maria" sees the comment "Should this retry on timeout?" on the "PaymentValidator" component

  Scenario: Comments on different components are kept separate
    Given "Petros" has started a Live Code Review Session for PR 42 in "acme/widgets"
    And "Maria" has joined that session
    When "Petros" comments "Looks good" on the "PaymentValidator" component
    And "Maria" comments "One concern here" on the "OrderService" component
    Then "Maria" sees the comment "Looks good" on the "PaymentValidator" component
    And "Petros" sees the comment "One concern here" on the "OrderService" component
    And "Petros" does not see the comment "One concern here" on the "PaymentValidator" component

  Scenario: A blank comment is rejected
    Given "Petros" has started a Live Code Review Session for PR 42 in "acme/widgets"
    When "Petros" attempts to comment "   " on the "PaymentValidator" component
    Then the attempt is rejected as invalid
