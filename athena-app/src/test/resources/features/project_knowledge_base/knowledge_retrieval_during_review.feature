Feature: Knowledge retrieval during review
  When a Knowledge Provider is configured, Athena retrieves knowledge
  relevant to the current review's changed files and includes it as
  contextual evidence for AI reasoning — never as authoritative truth, and
  never the whole knowledge base at once (ticket #118). Reviews behave
  exactly as before when no provider is configured, or when the provider
  fails, so a Knowledge Provider is never a hard dependency of a review.

  Scenario: Relevant project knowledge is retrieved and passed to the AI reasoning stage
    Given an Obsidian vault configured as the Knowledge Provider
    And the vault contains a note titled "Payment Service Ownership" mentioning "payment-service"
    And the reviewer has assembled a Review Context for a PR that changes a file under "payment-service"
    When the reviewer triggers AI analysis for the knowledge-aware review
    Then the request sent to the AI provider includes the note "Payment Service Ownership"
    And the request sent to the AI provider states that project knowledge is contextual evidence, not authoritative

  Scenario: Irrelevant notes are not included
    Given an Obsidian vault configured as the Knowledge Provider
    And the vault contains a note titled "Unrelated Notes" mentioning "billing-export"
    And the reviewer has assembled a Review Context for a PR that changes a file under "payment-service"
    When the reviewer triggers AI analysis for the knowledge-aware review
    Then the request sent to the AI provider does not include the note "Unrelated Notes"

  Scenario: A review proceeds normally with no Knowledge Provider configured
    Given no Knowledge Provider has been configured
    And the reviewer has assembled a Review Context for a PR that changes a file under "payment-service"
    When the reviewer triggers AI analysis for the knowledge-aware review
    Then the AI analysis completes normally
    And the request sent to the AI provider includes no project knowledge

  Scenario: A Knowledge Provider failure does not prevent the review from completing
    Given an Obsidian vault configured as the Knowledge Provider, but the vault directory has since been deleted
    And the reviewer has assembled a Review Context for a PR that changes a file under "payment-service"
    When the reviewer triggers AI analysis for the knowledge-aware review
    Then the AI analysis completes normally
    And the request sent to the AI provider includes no project knowledge
