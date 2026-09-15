Feature: Knowledge (Obsidian) settings page
  Project Knowledge configuration lives in its own settings page (ticket
  #118), reachable next to GitHub Access, as another optional External
  Systems / Integrations entry — never a blocking gate, and never required
  for the rest of Athena to work.

  Scenario: A user with no Knowledge Provider configured sees a way to connect
    Given no Knowledge Provider has been configured
    When the user opens the Knowledge settings page
    Then the page shows the Knowledge Provider as not configured
    And the page shows a way to configure an Obsidian vault

  Scenario: A user configures an Obsidian vault
    Given no Knowledge Provider has been configured
    When the user opens the Knowledge settings page
    And the user submits a vault path
    Then the page shows the Knowledge Provider as configured
    And the page shows the configured vault path

  Scenario: A user disconnects a configured Knowledge Provider
    Given an Obsidian vault is already configured as the Knowledge Provider
    When the user opens the Knowledge settings page
    And the user disconnects the Knowledge Provider
    Then the page shows the Knowledge Provider as not configured

  Scenario: The main page links to the Knowledge settings page, alongside GitHub Access
    Given the user is connected to GitHub
    When the user opens the main page
    Then the main page shows a way to reach the Knowledge settings page
