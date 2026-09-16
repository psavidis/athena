Feature: Live Code Review Session — shared navigation, follow, and present
  Inside a Live Code Review Session, one participant presents the shared
  view while everyone else follows it; any participant can step away to
  explore independently and return, or take control themselves (ticket
  #158). The presenter's identity is visible to everyone in the session.

  Scenario: The presenter moves the shared focus and it updates for everyone
    Given "Petros" has started a Live Code Review Session for PR 42 in "acme/widgets"
    And "Maria" has joined that session
    When "Petros" moves the shared focus to the "PaymentValidator" component
    Then the session's shared focus is the "PaymentValidator" component

  Scenario: A participant other than the presenter cannot move the shared focus
    Given "Petros" has started a Live Code Review Session for PR 42 in "acme/widgets"
    And "Maria" has joined that session
    When "Maria" attempts to move the shared focus to the "PaymentValidator" component
    Then the live session request is rejected as invalid

  Scenario: A participant takes control of the shared presentation
    Given "Petros" has started a Live Code Review Session for PR 42 in "acme/widgets"
    And "Maria" has joined that session
    When "Maria" takes control of the session
    Then "Maria" is the session's presenter
    And "Petros" is no longer the session's presenter

  Scenario: A participant explores independently without disrupting the shared view
    Given "Petros" has started a Live Code Review Session for PR 42 in "acme/widgets"
    And "Maria" has joined that session
    And "Petros" has moved the shared focus to the "PaymentValidator" component
    When "Maria" explores the "OrderService" component independently
    Then "Maria" is shown as exploring the "OrderService" component
    And the session's shared focus is still the "PaymentValidator" component

  Scenario: A participant returns to the shared view after exploring independently
    Given "Petros" has started a Live Code Review Session for PR 42 in "acme/widgets"
    And "Maria" has joined that session
    And "Petros" has moved the shared focus to the "PaymentValidator" component
    And "Maria" is exploring the "OrderService" component independently
    When "Maria" returns to the shared view
    Then "Maria" is following the shared focus
    And the session's shared focus is still the "PaymentValidator" component

  Scenario: A participant who starts exploring independently gives up any control they held
    Given "Petros" has started a Live Code Review Session for PR 42 in "acme/widgets"
    And "Maria" has joined that session
    And "Maria" has taken control of the session
    When "Maria" explores the "OrderService" component independently
    Then "Maria" is no longer the session's presenter
