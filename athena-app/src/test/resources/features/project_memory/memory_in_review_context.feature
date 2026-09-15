Feature: Project Memory in Review Context
  Athena's project memory (ticket 121) already accumulates learned
  facts from git history, Pull Requests, and external knowledge
  (tickets 170 through 172); this feature lets that memory also inform
  a review itself. A fact relevant to the PR's changed files is
  included as additional context for AI reasoning, alongside the diff
  and any project knowledge — framed as a historical pattern, never
  authoritative, and never the whole memory store dumped into every
  prompt. Reviews behave exactly as before when the project has no
  recorded memory yet.

  Scenario: A memory fact relevant to the PR's changed files is included as context for AI reasoning
    Given a project whose memory contains the fact "OrderService.java and OrderProjection.java change together"
    And the reviewer has assembled a Review Context for a PR that changes a file named "OrderService.java"
    When the reviewer triggers AI analysis for the memory-aware review
    Then the request sent to the AI provider includes the fact "OrderService.java and OrderProjection.java change together"
    And the request sent to the AI provider states that project memory is a historical pattern, not a hard rule

  Scenario: A memory fact irrelevant to the PR's changed files is not included
    Given a project whose memory contains the fact "Invoice.java and InvoiceView.java change together"
    And the reviewer has assembled a Review Context for a PR that changes a file named "OrderService.java"
    When the reviewer triggers AI analysis for the memory-aware review
    Then the request sent to the AI provider does not include the fact "Invoice.java and InvoiceView.java change together"

  Scenario: A review proceeds normally with no project memory recorded
    Given a project with no recorded memory
    And the reviewer has assembled a Review Context for a PR that changes a file named "OrderService.java"
    When the reviewer triggers AI analysis for the memory-aware review
    Then the AI analysis completes normally
    And the request sent to the AI provider includes no project memory
