Feature: Athena recognizes simple refactorings end to end
  A developer applies one refactoring to a small repository of plain classes,
  and Athena, given the two commits, shows it the way a reviewer would describe
  it: one Change naming the refactoring, not the scattered line edits a plain
  diff shows (tickets #384–#387). Each scenario runs the whole product path:
  git checkout, analysis, and the Change Map and Explorer the web UI shows.

  Background:
    Given a repository whose classes "Account", "Bank" and "Audit" use each other

  # Ticket #384: a rename and its reference updates are one Change.

  Scenario: A field rename reads as one rename
    When the developer renames "balance" to "funds" in "Account"
    And the reviewer opens the Change Map of the two commits
    Then the Change Map lists exactly "Rename field Account#balance -> funds"
    And the Explorer shows no "Mechanical Replacement" entry

  Scenario: A method rename used from other files reads as one rename with its references
    When the developer renames "getBalance" to "currentFunds" in "Account", "Bank" and "Audit"
    And the reviewer opens the Change Map of the two commits
    Then the Change Map lists exactly "Rename Account#getBalance -> currentFunds (references updated in 2 files)"
    And the Explorer shows no "Mechanical Replacement" entry
