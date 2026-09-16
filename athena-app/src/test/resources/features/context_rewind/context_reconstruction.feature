Feature: Context Rewind: reconstructing an entity's context from project history
  Context Rewind (ticket #161) reconstructs why a code entity exists, how
  it evolved, and what was decided about it, by aggregating Athena's
  existing sources of project history rather than inventing a new one:
  git and Pull Request history (epic #3), project memory's learned facts
  (ticket #121), and an optional Knowledge Provider (ticket #118). Every
  piece of reconstructed context is labeled by the source it came from,
  none of these sources is a hard dependency of another, and when the
  available history is too thin to say anything useful, Athena says so
  rather than fabricating an answer.

  Scenario: Reconstructing context for an entity aggregates its available history and knowledge
    Given a project whose git history and Pull Requests both mention a class "PaymentProcessor"
    And that project's memory contains the fact "PaymentProcessor.java and RetryWorker.java change together" about "PaymentProcessor"
    And an Obsidian vault configured as the Knowledge Provider contains a note about "PaymentProcessor"
    When Athena reconstructs the context for "PaymentProcessor"
    Then the reconstructed context includes the git commits and Pull Requests that touched "PaymentProcessor"
    And the reconstructed context includes the project memory fact about "PaymentProcessor"
    And the reconstructed context includes the Knowledge Provider note about "PaymentProcessor"

  Scenario: Reconstructed context labels each piece of information by its source
    Given a project whose git history and Pull Requests both mention a class "PaymentProcessor"
    And that project's memory contains the fact "PaymentProcessor.java and RetryWorker.java change together" about "PaymentProcessor"
    And an Obsidian vault configured as the Knowledge Provider contains a note about "PaymentProcessor"
    When Athena reconstructs the context for "PaymentProcessor"
    Then the git and Pull Request history in the reconstructed context is labeled as historical fact
    And the project memory fact in the reconstructed context is labeled as historical fact
    And the Knowledge Provider note in the reconstructed context is labeled as knowledge-base information, not a project fact

  Scenario: Context Rewind still functions when no Knowledge Provider is configured
    Given a project whose git history mentions a class "PaymentProcessor" in 2 commits
    And no Knowledge Provider has been configured
    When Athena reconstructs the context for "PaymentProcessor"
    Then the reconstructed context includes the git commits that touched "PaymentProcessor"
    And the reconstructed context includes no Knowledge Base information
    And reconstructing the context does not fail

  Scenario: Athena reports insufficient history instead of fabricating context
    Given a project whose git history, Pull Requests, and project memory contain nothing about a class "UntouchedHelper"
    And no Knowledge Provider has been configured
    When Athena reconstructs the context for "UntouchedHelper"
    Then the reconstructed context reports that insufficient historical information is available for "UntouchedHelper"
    And the reconstructed context includes no AI-generated interpretation
