Feature: Semantic Canvas — File-First review mode
  An experienced reviewer who already knows the codebase can switch the
  Semantic Canvas to File-First mode (ticket #132): a flat, searchable
  file list grouped by module, reaching a file's diff in one click
  without being routed through Athena's semantic explanation first.

  Scenario: The reviewer switches to File-First mode from the topbar
    Given the reviewer is viewing the Semantic Canvas in Contextual mode
    When the reviewer selects File-First in the Review mode control
    Then the canvas viewport is replaced by a flat file list

  Scenario: The file list groups files by module with a real file count
    Given a PR touching 3 files in "crowdness-live" and 2 files in "crowdness-ingestion"
    When the reviewer is viewing File-First mode
    Then the reviewer sees a "crowdness-live" group showing 3 files
    And the reviewer sees a "crowdness-ingestion" group showing 2 files

  Scenario: A module group can be collapsed and expanded independently of others
    Given the reviewer is viewing File-First mode with two module groups
    When the reviewer collapses the "crowdness-live" group
    Then the "crowdness-live" group's files are hidden
    And the "crowdness-ingestion" group's files are still shown

  Scenario: A config/build file shows the same distinct icon used on the canvas
    Given the file list includes the config file "application.yml" and the production file "OrderService.java"
    When the reviewer is viewing File-First mode
    Then "application.yml" shows the config icon
    And "OrderService.java" shows the generic file icon

  Scenario: The reviewer filters the file list by typing a search term
    Given the file list includes "OrderService.java" and "PaymentValidator.java"
    When the reviewer types "Payment" into the file search
    Then only "PaymentValidator.java" is shown

  Scenario: The reviewer filters the file list by module name
    Given the file list includes files from "crowdness-live" and "crowdness-ingestion"
    When the reviewer types "ingestion" into the file search
    Then only files from "crowdness-ingestion" are shown

  Scenario: The reviewer applies the "New code" quick filter
    Given the file list includes a newly added file and a modified file
    When the reviewer applies the "New code" quick filter
    Then only the newly added file is shown

  Scenario: The reviewer applies the "Has context" quick filter
    Given one file has a matching node on the semantic canvas and another does not
    When the reviewer applies the "Has context" quick filter
    Then only the file with a matching canvas node is shown

  Scenario: Clicking a file row opens its diff directly in the detail drawer
    Given the reviewer is viewing File-First mode
    When the reviewer clicks the "OrderService.java" row
    Then the detail drawer opens showing the diff for "OrderService.java"

  Scenario: "Explain this" switches to Contextual mode and jumps to the file's canvas node
    Given "PaymentValidator.java" has a matching node on the semantic canvas
    When the reviewer selects "Explain this" for "PaymentValidator.java"
    Then the canvas switches to Contextual mode
    And the camera lands on "PaymentValidator.java"'s Structure-altitude node

  Scenario: A file with no matching canvas node has no "Explain this" action
    Given "GeneratedConfig.java" has no matching node on the semantic canvas
    When the reviewer is viewing File-First mode
    Then "GeneratedConfig.java"'s row has no "Explain this" action

  Scenario: Switching back to Contextual mode returns to the territory map
    Given the reviewer is viewing File-First mode
    When the reviewer selects Contextual in the Review mode control
    Then the reviewer sees the territory map
