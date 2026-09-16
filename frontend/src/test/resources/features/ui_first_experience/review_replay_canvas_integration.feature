Feature: Review Replay — Semantic Canvas integration
  Selecting a moment on a Review Replay's timeline (ticket #211) takes the
  developer to the relevant part of the Semantic Canvas: the module that
  moment's entity belongs to, reusing the same canvas-overlay/focus
  pattern Context Rewind (#187, #191, #194) and the Live Code Review
  Session's own territory-follow mechanism (ticket #158) already
  established, rather than inventing a new navigation mechanism (ticket
  #212).

  Re-scoped during spec-writing (see #212's own "no scope beyond what the
  canvas already supports" limitation, taken literally): the canvas's
  existing external-navigation precedents (Context Rewind's
  `onOpenModule`, the Live Session's `sharedTerritory`) are deliberately
  territory/module-grained, not node- or diff-location-grained —
  `selectNode`/the detail drawer's selection require a full topology
  entry only code already inside the canvas page has, not reachable from
  an external reference string. So this ticket delivers entity-to-module
  focus, restoring the canvas's normal zoom-altitude state at that
  territory; it does not attempt to jump directly to a diff line or
  visually connect several entities as one drawn "path" — both require
  canvas-navigation capabilities that don't exist yet as an external
  contract, and are flagged back as follow-up scope rather than guessed
  at here.

  Scenario: Selecting a moment focuses the canvas on the module its entity belongs to
    Given a Replay timeline with a confirmed question about the "OrderService" entity
    And "OrderService" lives in the "orders" module
    When the developer selects that moment
    Then the Semantic Canvas is focused on the "orders" module

  Scenario: Selecting a moment whose entity's module can't be determined leaves the canvas unchanged
    Given the Semantic Canvas is focused on the "payments" module
    And a Replay timeline with a confirmed moment about an entity with no known module
    When the developer selects that moment
    Then the Semantic Canvas is still focused on the "payments" module

  Scenario: Selecting a moment whose reference no longer resolves leaves the canvas unchanged
    Given the Semantic Canvas is focused on the "payments" module
    And a Replay timeline with a moment whose entity reference no longer resolves
    When the developer selects that unresolved moment
    Then the Semantic Canvas is still focused on the "payments" module

  Scenario: Selecting a different moment moves the canvas focus to that moment's module
    Given a Replay timeline with a confirmed question about "OrderService" in the "orders" module, then a confirmed decision about "PaymentGateway" in the "payments" module
    And the developer has selected the question, focusing the canvas on "orders"
    When the developer selects the decision
    Then the Semantic Canvas is focused on the "payments" module
