Feature: Context Rewind — semantic zoom for history
  Context Rewind (ticket #188) reuses Athena's semantic zooming model: the
  amount of information increases as the developer zooms in, rather than
  presenting everything at once. Three stops — Orientation (where the
  entity sits), Overview (its evolution timeline, Pull Requests, and
  narrative — ticket #187's own view), and Detail (a single selected
  event's own evidence, full focus).

  Scenario: Opening Context Rewind lands at the Orientation level, showing only where the entity sits
    Given the developer opens Context Rewind for "PaymentProcessor" reached from the "Payment processing" concept in the "crowdness-live" module
    Then Context Rewind shows "crowdness-live › Payment processing › PaymentProcessor"
    And it does not show the evolution timeline yet

  Scenario: Orientation shows just the entity name when no location breadcrumb is available
    Given the developer opens Context Rewind for "PaymentProcessor" with no known module or concept
    Then Context Rewind shows "PaymentProcessor" at the Orientation level

  Scenario: Zooming in from Orientation reveals the Overview — the entity's timeline, Pull Requests, and narrative
    Given the developer has Context Rewind open for "PaymentProcessor" at the Orientation level
    When the developer zooms in
    Then Context Rewind shows "PaymentProcessor"'s evolution timeline
    And it shows the Pull Requests that touched "PaymentProcessor"

  Scenario: Zooming in on a selected timeline event reveals its Detail, focused solely on that event
    Given the developer has Context Rewind open for "PaymentProcessor" at the Overview level with the "Retry mechanism added" event selected
    When the developer zooms in
    Then Context Rewind shows only "Retry mechanism added"'s own evidence, full width
    And it no longer shows the rest of the timeline

  Scenario: Zooming out from Detail returns to Overview with the same event still selected
    Given the developer has Context Rewind open for "PaymentProcessor" at the Detail level for the "Retry mechanism added" event
    When the developer zooms out
    Then Context Rewind returns to the Overview level
    And the "Retry mechanism added" event is still selected

  Scenario: No "Zoom in" action is offered from the Overview level when nothing is selected
    Given the developer has Context Rewind open for "PaymentProcessor" at the Overview level with nothing selected
    Then no "Zoom in" action is offered
