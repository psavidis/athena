Feature: ESLint as a concrete external analysis provider
  Athena integrates ESLint (ticket #114/#117) as a real, concrete
  `ExternalAnalysisProvider` implementation for JavaScript — the
  architecture itself was already proven with a fake provider in
  external_findings_as_evidence.feature and with a second real provider,
  PMD, for Java (ticket #114/#116, pmd_analysis_provider.feature); this
  exercises the genuine ESLint CLI, run against the analyzed project's own
  local installation and configuration, end to end through `PrAnalyzer`.
  ESLint's own findings surface as ordinary `ExternalFinding`s alongside
  Athena's own Change/SemanticProfile analysis, distinguishable by
  provider id "eslint", and ESLint being unavailable or failing to run
  (e.g. not installed in the analyzed project — this ticket deliberately
  does not auto-install it) degrades gracefully rather than blocking
  Athena's own analysis.

  Scenario: An ESLint-detectable violation surfaces as a finding attributed to ESLint
    Given ESLint is configured as a provider
    And a JavaScript project with ESLint installed and configured
    And a JavaScript file "app.js" with an unused variable
    When the PR is analyzed with ESLint configured as a provider
    Then the analysis result includes a finding from "eslint" on "app.js"

  Scenario: JavaScript code with no ESLint-detectable issues produces no ESLint findings
    Given ESLint is configured as a provider
    And a JavaScript project with ESLint installed and configured
    And a JavaScript file "clean.js" with no ESLint-detectable issues
    When the PR is analyzed with ESLint configured as a provider
    Then the analysis result includes no finding from "eslint"

  Scenario: ESLint not being installed in the analyzed project degrades gracefully without blocking Athena's own analysis
    Given ESLint is configured as a provider
    And a JavaScript project without ESLint installed
    And a base and head revision where a symbol is renamed
    When the PR is analyzed with ESLint configured as a provider
    Then the analysis result still classifies the renamed symbol as a Change
    And the analysis result includes no finding from "eslint"
