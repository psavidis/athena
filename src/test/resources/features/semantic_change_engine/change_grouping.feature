Feature: Change grouping and exceptions
  Multiple individually-detected transformations that represent one
  conceptual operation collapse into a single Change, so a reviewer sees
  one meaningful item instead of many fragments. Anything that resembles
  the group's pattern without structurally matching it is surfaced as an
  exception, still linked to the parent Change.

  Scenario: Several occurrences of the same rename pattern collapse into one Change
    Given 4 detected renames all following the pattern "Foo" to "Bar"
    When the semantic engine groups the detected transformations into Changes
    Then one Change is produced for the "Foo" to "Bar" rename
    And that Change reports 4 occurrences
    And that Change reports 0 exceptions

  Scenario: An occurrence that doesn't structurally match the group's pattern becomes an exception
    Given 3 detected renames following the pattern "Foo" to "Bar"
    And 1 detected rename with the same names but a different transformation shape
    When the semantic engine groups the detected transformations into Changes
    Then one Change is produced for the "Foo" to "Bar" rename
    And that Change reports 3 occurrences
    And that Change reports 1 exception
    And the Change's exceptions can be listed on their own

  Scenario: A single standalone transformation becomes its own Change
    Given 1 detected extract-method transformation for "Greeter#buildGreeting"
    When the semantic engine groups the detected transformations into Changes
    Then one Change is produced for "Greeter#buildGreeting"
    And that Change reports 1 occurrence
    And that Change reports 0 exceptions
