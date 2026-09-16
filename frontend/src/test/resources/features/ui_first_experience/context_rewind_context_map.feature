Feature: Context Rewind — Context Map
  Context Rewind (ticket #191) offers a Context Map from the Orientation
  level: a relationship view of the entity's module and its direct
  dependencies/dependents, reusing the same module topology data the
  Semantic Canvas already models — not a new dependency-graph extraction.

  Re-scoped during spec-writing (flagged to and confirmed with the user):
  the only relationship data Athena models today is module-level
  (`ModuleTopology`), not class-level, so the map shows module
  relationships, not "PaymentProcessor depends on RetryWorker"-style class
  relationships. Selecting a related module returns to the Semantic
  Canvas focused there — it does not navigate to another entity's own
  Context Rewind, since there's no class-level node to navigate to.

  Scenario: Opening the Context Map shows the entity's module and its direct relationships
    Given "PaymentProcessor" lives in the "crowdness-live" module
    And "crowdness-live" depends on "crowdness-ingestion"
    And "crowdness-payments-api" depends on "crowdness-live"
    When the developer opens the Context Map for "PaymentProcessor"
    Then the map shows "crowdness-live" depends on "crowdness-ingestion"
    And it shows "crowdness-payments-api" depends on "crowdness-live"

  Scenario: Selecting a related module returns to the canvas focused there
    Given the developer has the Context Map open for "PaymentProcessor" in the "crowdness-live" module
    When the developer selects the related module "crowdness-ingestion"
    Then the developer returns to the Semantic Canvas focused on "crowdness-ingestion"

  Scenario: The Context Map isn't offered when the entity's module isn't known
    Given the developer opens Context Rewind for "PaymentProcessor" with no known module
    Then no Context Map action is offered
