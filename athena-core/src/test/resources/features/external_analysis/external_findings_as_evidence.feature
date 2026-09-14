Feature: External findings as evidence for Athena's analysis
  Configured external analysis providers (ticket #114/#149, e.g. SonarJava,
  ESLint) surface their own findings alongside Athena's own Change/
  SemanticProfile analysis — as independent evidence, never merged into or
  deriving Athena's own semantic classification, and isolated from each
  other so one provider's failure never takes down the rest of the run.

  Scenario: A configured provider's findings are available on the analysis result
    Given a provider "sonarjava" configured to report a finding on "Order.java"
    When the PR is analyzed
    Then the analysis result includes a finding from "sonarjava" on "Order.java"

  Scenario: Findings can be retrieved for a specific file
    Given a provider "sonarjava" configured to report findings on "Order.java" and "Payment.java"
    When the PR is analyzed
    Then the analysis result's findings for "Order.java" only include the "Order.java" finding

  Scenario: Two providers' findings coexist on the same analysis, each attributed to its own provider
    Given a provider "sonarjava" configured to report a finding on "Order.java"
    And a provider "eslint" configured to report a finding on "app.js"
    When the PR is analyzed
    Then the analysis result includes a finding from "sonarjava" on "Order.java"
    And the analysis result includes a finding from "eslint" on "app.js"

  Scenario: One provider's failure does not prevent Athena's own analysis from completing
    Given a provider "sonarjava" configured to fail with "sonar-scanner not found"
    And the PR renames a symbol between its base and head revisions
    When the PR is analyzed
    Then the analysis result still detects the rename as a Change

  Scenario: One provider's failure does not prevent another provider's findings from being available
    Given a provider "sonarjava" configured to fail with "sonar-scanner not found"
    And a provider "eslint" configured to report a finding on "app.js"
    When the PR is analyzed
    Then the analysis result includes a finding from "eslint" on "app.js"

  Scenario: Analyzing with no external providers configured behaves exactly as before
    Given no external providers are configured
    And the PR renames a symbol between its base and head revisions
    When the PR is analyzed
    Then the analysis result still detects the rename as a Change
    And the analysis result includes no external findings

  Scenario: External findings never alter Athena's own semantic classification
    Given a provider "sonarjava" configured to report a finding on "Order.java"
    And the PR renames a symbol between its base and head revisions
    When the PR is analyzed
    Then the analysis result's Change classification is unaffected by the external finding
