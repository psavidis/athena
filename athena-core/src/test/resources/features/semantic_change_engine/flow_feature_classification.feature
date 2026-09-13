Feature: Flow/Feature classification of code changes
  A reviewer needs a change to be classified with the product Flow/use-case
  step it participates in, so that the Semantic Change Explorer's Flow
  level (issue #91) can show where a change sits in the surrounding
  behavior instead of leaving that level empty (ticket #92).

  Scenario: A change correlating with a known flow is classified with that flow's concept
    Given a change that adds a class named "CheckoutService" to the codebase
    When the change is classified along the Feature dimension
    Then it is classified with the "Checkout" flow concept

  Scenario: A change with no correlating flow concept is left unclassified
    Given a change that adds a class named "MathUtils" to the codebase
    When the change is classified along the Feature dimension
    Then it has no Feature classification

  Scenario: A change with no matched code at all is left unclassified
    Given a change with no matched code at all
    When the change is classified along the Feature dimension
    Then it has no Feature classification

  Scenario: A change's Feature classification is visible in its overall semantic profile
    Given a change that adds a class named "CheckoutService" to the codebase
    When the change is classified along the Feature dimension
    And its semantic profile is assembled
    Then the semantic profile includes the "Checkout" flow concept for the Feature dimension
