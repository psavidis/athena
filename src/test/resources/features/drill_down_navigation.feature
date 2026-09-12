Feature: Multi-level drill-down navigation
  A reviewer moves fluidly between abstraction levels: from a Change down
  to its symbols, files, and diff, and from any file back up to the Change
  it belongs to (epic #5 §15). Raw diff access is never blocked by
  classification state.

  Scenario: Opening a Change shows its category, description, symbols, and files
    Given a Change representing a rename between two symbols
    When the reviewer opens the Change's detail view
    Then the detail view shows the Change's category
    And the detail view shows the Change's description
    And the detail view lists the involved symbols
    And the detail view lists the touched files

  Scenario: A file can be traced back to the Change it belongs to
    Given a Change representing a rename that touches file "Greeter.java"
    When the reviewer looks up which Change owns file "Greeter.java"
    Then the owning Change is the rename Change

  Scenario: A file not touched by any Change has no owning Change
    Given a Change representing a rename that touches file "Greeter.java"
    When the reviewer looks up which Change owns file "Unrelated.java"
    Then there is no owning Change

  Scenario: Raw diff access does not depend on the Change's category
    Given a Change representing a mechanical replacement
    When the reviewer opens the Change's detail view
    Then the detail view still exposes the Change's underlying evidence
