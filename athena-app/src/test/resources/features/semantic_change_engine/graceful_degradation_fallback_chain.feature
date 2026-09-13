Feature: Graceful degradation fallback chain
  The Semantic Change Engine never blocks a reviewer from the underlying
  code, even when full semantic analysis is slow, partial, or fails
  outright. Analysis proceeds through three levels — Semantic Change
  Model, Symbol-aware diff, Traditional textual diff — and always reports
  an explicit status so the caller knows which level was reached
  (epic #4 §44, §45).

  Scenario: A fully analyzable revision pair reaches Ready with full semantic detail
    Given a base and head revision where every file parses and resolves cleanly
    When the engine analyzes the revision pair
    Then the analysis status is "Ready"
    And the analysis reports the detected Changes
    And the raw textual diff is still available

  Scenario: A revision pair with one unparseable file degrades to Partially analyzed
    Given a base and head revision where one file fails to parse and the rest parse cleanly
    When the engine analyzes the revision pair
    Then the analysis status is "Partially analyzed"
    And the analysis reports the Changes detected from the files that did parse
    And the analysis reports a symbol-aware diff entry for the file that failed to parse
    And the raw textual diff is still available for the file that failed to parse

  Scenario: A revision pair where every file is unparseable falls all the way back to Analysis failed
    Given a base and head revision where every file fails to parse
    When the engine analyzes the revision pair
    Then the analysis status is "Analysis failed"
    And the analysis reports no detected Changes
    And the raw textual diff is still available

  Scenario: A revision pair with multiple files, all unparseable, is still Analysis failed rather than Partially analyzed
    Given a base and head revision with two files, both of which fail to parse
    When the engine analyzes the revision pair
    Then the analysis status is "Analysis failed"

  Scenario: The raw diff remains accessible regardless of analysis status
    Given a base and head revision where every file fails to parse
    When the engine analyzes the revision pair
    Then a caller can retrieve the raw base/head diff for any file, independent of analysis status
