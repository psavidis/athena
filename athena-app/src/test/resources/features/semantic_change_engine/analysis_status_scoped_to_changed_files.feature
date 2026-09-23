Feature: Analysis status reflects only the files the change touches
  The analysis status and the list of files that fell back to a
  symbol-aware diff describe the change under review, not the rest of the
  repository, and current Java syntax is parsed (ticket #262).

  Scenario: An unparseable file outside the change does not degrade the status
    Given a base and head revision where the only changed file parses cleanly
    And an unchanged file elsewhere in the repository fails to parse in both revisions
    When the engine analyzes the revision pair
    Then the analysis status is "Ready"
    And no symbol-aware diff entry is reported for the unchanged file

  Scenario: An unparseable changed file still degrades the status
    Given a base and head revision where one changed file fails to parse and the other changed files parse cleanly
    When the engine analyzes the revision pair
    Then the analysis status is "Partially analyzed"
    And a symbol-aware diff entry is reported for the changed file that failed to parse

  Scenario: A changed file using Java 21 syntax is analyzed
    Given a base and head revision where a changed file uses a switch with type patterns and an unnamed pattern variable
    When the engine analyzes the revision pair
    Then the analysis status is "Ready"
    And the analysis reports the Changes detected in that file
