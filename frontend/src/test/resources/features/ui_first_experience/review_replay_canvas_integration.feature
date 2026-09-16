Feature: Review Replay — Semantic Canvas integration
  Selecting a moment on a Review Replay's timeline (ticket #211) takes the
  developer to the relevant part of the Semantic Canvas: the entity that
  moment concerns, at the zoom-altitude appropriate to that moment,
  reusing the same canvas-overlay/focus pattern Context Rewind (#187,
  #191, #194) already established rather than inventing a new navigation
  mechanism (ticket #212).

  Scenario: Selecting a moment focuses the canvas on the entity it references
    Given a Replay timeline with a confirmed question about the "OrderService" entity
    When the developer selects that moment
    Then the Semantic Canvas is focused on "OrderService"

  Scenario: Selecting a moment restores its own zoom-altitude context
    Given a Replay timeline with a confirmed decision about "OrderService" tagged at the "STRUCTURE" zoom level
    When the developer selects that moment
    Then the Semantic Canvas is at the "STRUCTURE" zoom level

  Scenario: Selecting a moment whose reference no longer resolves leaves the canvas unchanged
    Given the Semantic Canvas is focused on "PaymentService"
    And a Replay timeline with a moment whose entity reference no longer resolves
    When the developer selects that unresolved moment
    Then the Semantic Canvas is still focused on "PaymentService"

  Scenario: Selecting a chain of related moments highlights the reasoning path across their entities
    Given a Replay timeline with a confirmed question about "OrderService", then a confirmed decision about "PaymentService" in the same group
    When the developer selects that group
    Then the canvas highlights the reasoning path across "OrderService" and "PaymentService"

  Scenario: Selecting a moment referencing a diff location opens that location
    Given a Replay timeline with a confirmed moment referencing the diff for "OrderService.java"
    When the developer selects that moment
    Then the Semantic Canvas opens the diff for "OrderService.java"
