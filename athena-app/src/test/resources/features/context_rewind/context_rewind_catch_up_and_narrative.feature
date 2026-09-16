Feature: Context Rewind: catching up on an entity and its AI-generated narrative
  Beyond aggregating raw history (see context_reconstruction.feature),
  Context Rewind (ticket #161) gives a developer a way back into an area
  they haven't touched in a while: it points to the Pull Requests behind
  the history so they can be opened directly, it can scope that history
  to only what happened since the developer last interacted with the
  entity ("catch me up"), it orders an entity's history into a timeline
  of its evolution, and it may add an AI-generated narrative on top of
  the aggregated facts — always labeled as an interpretation, never
  persisted or presented as a project fact in its own right.

  Scenario: Reconstructed context surfaces the Pull Requests it drew from as navigable references
    Given a project whose Pull Request 217 added retry handling to a class "PaymentProcessor"
    When Athena reconstructs the context for "PaymentProcessor"
    Then the reconstructed context references Pull Request 217
    And that reference identifies the Pull Request's number and repository so it can be opened directly

  Scenario: "Catch me up" reconstructs only what changed since a developer's last interaction with an entity
    Given a developer last interacted with a class "PaymentProcessor" on "2026-01-01"
    And Pull Request 217 changed "PaymentProcessor" on "2026-02-01"
    And Pull Request 190 changed "PaymentProcessor" on "2025-12-01"
    When Athena reconstructs a "catch me up" context for "PaymentProcessor" for that developer
    Then the reconstructed context reports Pull Request 217 as activity since the developer last interacted with "PaymentProcessor"
    And the reconstructed context does not report Pull Request 190

  Scenario: Reconstructed context orders an entity's evolution as a timeline
    Given Pull Request 190 changed a class "PaymentProcessor" on "2025-12-01"
    And Pull Request 217 changed "PaymentProcessor" on "2026-02-01"
    When Athena reconstructs the context for "PaymentProcessor"
    Then the reconstructed context lists Pull Request 190 before Pull Request 217 in "PaymentProcessor"'s evolution timeline

  Scenario: An AI-generated narrative is included when available and clearly labeled as an interpretation
    Given a project whose git history and Pull Requests mention a class "PaymentProcessor"
    And the AI provider will generate a narrative summary of "PaymentProcessor"'s history
    When Athena reconstructs the context for "PaymentProcessor"
    Then the reconstructed context includes an AI-generated narrative about "PaymentProcessor"
    And that narrative is labeled as an AI-generated interpretation, not a project fact
