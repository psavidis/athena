Feature: Web session PR selection
  Selecting a new PR replaces any previously-selected one, cleaning up its
  checked-out revisions so temp directories don't accumulate across a
  browser session (ticket #73).

  Scenario: Selecting a new PR cleans up the previous selection's checkout directories
    Given the reviewer's web session has a PR selected with checked-out directories
    When the reviewer selects a different PR
    Then the previous PR's checkout directories no longer exist
    And the session's selected PR is now the new one
