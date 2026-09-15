Feature: PMD as a concrete external analysis provider
  Athena integrates PMD (ticket #114/#116) as a real, concrete
  `ExternalAnalysisProvider` implementation — the architecture itself was
  already proven with a fake provider in external_findings_as_evidence.feature;
  this exercises the genuine PMD engine, run in-process, end to end through
  `PrAnalyzer`. PMD's own findings surface as ordinary `ExternalFinding`s
  alongside Athena's own Change/SemanticProfile analysis, distinguishable by
  provider id "pmd", and PMD failing to run (e.g. a misconfigured ruleset)
  degrades gracefully rather than blocking Athena's own analysis.

  Scenario: A PMD-detectable violation surfaces as a finding attributed to PMD
    Given PMD is configured as a provider
    And a Java class "Sample.java" with an unused local variable
    When the PR is analyzed with PMD configured as a provider
    Then the analysis result includes a finding from "pmd" on "Sample.java"

  Scenario: Code with no PMD-detectable issues produces no PMD findings
    Given PMD is configured as a provider
    And a Java class "Clean.java" with no PMD-detectable issues
    When the PR is analyzed with PMD configured as a provider
    Then the analysis result includes no finding from "pmd"

  Scenario: A misconfigured PMD ruleset degrades gracefully without blocking Athena's own analysis
    Given PMD is configured with a ruleset it cannot load
    And a base and head revision where a symbol is renamed
    When the PR is analyzed with PMD configured as a provider
    Then the analysis result still classifies the renamed symbol as a Change
    And the analysis result includes no finding from "pmd"
