Feature: Knowledge candidate capture
  After a review, Athena may identify information worth persisting for
  future reviews. Athena proposes it as a knowledge candidate rather than
  persisting it automatically — the user confirms before anything is
  written to the Knowledge Provider (ticket #118). Each captured item
  retains its provenance: source, creation time, and repository context.

  Scenario: A user saves an accepted finding to the Knowledge Base
    Given an Obsidian vault configured as the Knowledge Provider
    And the reviewer has accepted an AI finding "Retry configuration intentionally overridden per environment"
    When the user saves that finding to the Knowledge Base
    Then the Knowledge Provider stores a new knowledge item with that content
    And the stored knowledge item's provenance names "obsidian" as its source

  Scenario: Saving a knowledge candidate is rejected when no Knowledge Provider is configured
    Given no Knowledge Provider has been configured
    And the reviewer has accepted an AI finding "Retry configuration intentionally overridden per environment"
    When the user attempts to save that finding to the Knowledge Base
    Then the save attempt is rejected because no Knowledge Provider is configured
