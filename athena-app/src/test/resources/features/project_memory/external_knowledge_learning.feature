Feature: External Knowledge as a Project Memory Learning Source
  Athena's Knowledge Provider (ticket 118) already retrieves relevant
  user-authored knowledge at review time; this feature lets that same
  retrieved knowledge also become an input to Athena's project memory,
  not just transient review context. A retrieved knowledge item becomes
  a memory fact whose evidence names the note and Knowledge Provider it
  came from, so the fact's origin stays traceable. Learning from
  external knowledge is one-directional: the user's knowledge base is
  never written to as a side effect of this.

  Scenario: Relevant external knowledge becomes a learned memory fact with its source as provenance
    Given an Obsidian vault configured as the Knowledge Provider
    And the vault contains a note titled "Reporting Adapter Exception" mentioning "reporting-adapters"
    And relevant external knowledge for the term "reporting-adapters" has already been retrieved
    When Athena learns from that project's external knowledge
    Then querying that project's memory returns a fact that mentions "reporting-adapters"
    And that fact's evidence names the note "Reporting Adapter Exception" from the "obsidian" Knowledge Provider

  Scenario: Multiple retrieved knowledge items each become their own learned fact
    Given an Obsidian vault configured as the Knowledge Provider
    And the vault contains a note titled "Reporting Adapter Exception" mentioning "reporting-adapters"
    And the vault contains a note titled "Payment Retry Policy" mentioning "payment-retry"
    And relevant external knowledge for the term "reporting-adapters" has already been retrieved
    And relevant external knowledge for the term "payment-retry" has already been retrieved
    When Athena learns from that project's external knowledge
    Then querying that project's memory returns a fact that mentions "reporting-adapters"
    And querying that project's memory returns a fact that mentions "payment-retry"

  Scenario: A project with no relevant external knowledge has no learned memory
    Given no relevant external knowledge has been retrieved for this project
    When Athena learns from that project's external knowledge
    Then Athena's external-knowledge learning leaves that project's memory empty
    And external-knowledge learning does not fail with an exception

  Scenario: Learning from the same knowledge again does not duplicate an already-learned pattern
    Given an Obsidian vault configured as the Knowledge Provider
    And the vault contains a note titled "Reporting Adapter Exception" mentioning "reporting-adapters"
    And relevant external knowledge for the term "reporting-adapters" has already been retrieved
    And Athena has already learned from that project's external knowledge once
    When Athena learns from that project's external knowledge again
    Then querying that project's memory returns exactly one fact that mentions "reporting-adapters"

  Scenario: Learning from external knowledge never writes back to the user's knowledge base
    Given an Obsidian vault configured as the Knowledge Provider
    And the vault contains a note titled "Reporting Adapter Exception" mentioning "reporting-adapters"
    And relevant external knowledge for the term "reporting-adapters" has already been retrieved
    When Athena learns from that project's external knowledge
    Then the vault's notes are left unchanged
