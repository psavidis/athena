Feature: Module-level "what changed and why" narratives
  Above the Change Map's per-Change rows, a reviewer can see the PR's
  Changes clustered by top-level module, and ask for an AI-generated
  narrative explaining why a module's Changes belong together — a
  business-semantic summary a flat list of signature changes can't give on
  its own.

  Scenario: Reviewer lists the PR's module groups without triggering any AI call
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    When the reviewer requests the module groups
    Then the response includes a module group covering the rename Change
    And no AI narrative call was made

  Scenario: Reviewer requests a module's narrative
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And an AI narrative provider is configured that explains a module as "Renames a greeting method for clarity."
    When the reviewer requests the narrative for that module
    Then the narrative response says "Renames a greeting method for clarity."

  Scenario: A module's narrative is only generated once and then cached
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And an AI narrative provider is configured that explains a module as "Renames a greeting method for clarity."
    When the reviewer requests the narrative for that module twice
    Then the AI narrative provider was called only once

  Scenario: Requesting a narrative without an AI provider configured fails
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    But no AI narrative provider is configured
    When the reviewer requests the narrative for that module
    Then the request is rejected as service unavailable

  Scenario: Requesting the narrative for an unknown module fails
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And an AI narrative provider is configured that explains a module as "Renames a greeting method for clarity."
    When the reviewer requests the narrative for module "does-not-exist"
    Then the request is rejected because the module was not found
