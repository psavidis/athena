Feature: Knowledge Provider configuration
  Project Knowledge (ticket #118) is an optional external integration,
  configured in the same External Systems / Integrations area as the Pull
  Request provider (GitHub) — never a mandatory part of Athena's review
  pipeline. Athena must work fully, with no errors or warnings, when no
  Knowledge Provider is configured.

  Scenario: No Knowledge Provider is configured
    Given no Knowledge Provider has been configured
    When the user checks the Knowledge Provider status
    Then the Knowledge Provider is reported as not configured
    And no error is reported

  Scenario: A user configures an Obsidian vault as the Knowledge Provider
    Given an Obsidian vault at a real directory on disk
    When the user configures that directory as the Obsidian Knowledge Provider
    Then the Knowledge Provider is reported as configured with provider "obsidian"
    And the Knowledge Provider status shows the configured vault path

  Scenario: Configuring a vault path that does not exist is rejected
    When the user configures a nonexistent directory as the Obsidian Knowledge Provider
    Then the configuration attempt is rejected
    And the Knowledge Provider is still reported as not configured

  Scenario: A user disconnects a configured Knowledge Provider
    Given an Obsidian vault configured as the Knowledge Provider
    When the user disconnects the Knowledge Provider
    Then the Knowledge Provider is reported as not configured
