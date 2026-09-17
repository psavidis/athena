Feature: Review Briefing — Semantic Canvas navigation
  Selecting a focus area, question, or other briefing item takes the
  developer directly to the relevant part of the Semantic Canvas — the
  module that item's entity belongs to — instead of opening a separate
  page, reusing Review Replay's entity-to-module resolution (ticket
  #212) and the canvas's existing module-focus external-navigation
  contract (ticket #223's overlay pattern). Starting the review also
  jumps the canvas to the recommended starting point's module before
  collapsing the overlay.

  Scenario: Selecting a briefing item focuses the canvas on the module its entity belongs to
    Given a Review Briefing overlay with a focus area about the "OrderService" entity
    And "OrderService" lives in the "orders" module
    When the developer selects that focus area
    Then the Semantic Canvas is focused on the "orders" module

  Scenario: Selecting a briefing item whose entity's module can't be determined leaves the canvas unchanged
    Given the Semantic Canvas is focused on the "payments" module
    And a Review Briefing overlay with a question about an entity with no known module
    When the developer selects that question
    Then the Semantic Canvas is still focused on the "payments" module

  Scenario: Selecting a briefing item with no entity reference leaves the canvas unchanged
    Given the Semantic Canvas is focused on the "payments" module
    And a Review Briefing overlay with a repo-wide observation that concerns no specific entity
    When the developer selects that observation
    Then the Semantic Canvas is still focused on the "payments" module

  Scenario: Starting the review jumps the canvas to the recommended starting point
    Given a Review Briefing overlay whose recommended starting point concerns the "PaymentGateway" entity
    And "PaymentGateway" lives in the "payments" module
    When the developer starts the review
    Then the Semantic Canvas is focused on the "payments" module
    And the Review Briefing overlay is collapsed to an indicator

  Scenario: Starting the review with no recommended starting point leaves the canvas unchanged
    Given the Semantic Canvas is focused on the "orders" module
    And a Review Briefing overlay with no recommended starting point
    When the developer starts the review
    Then the Semantic Canvas is still focused on the "orders" module
    And the Review Briefing overlay is collapsed to an indicator
