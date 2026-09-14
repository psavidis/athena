Feature: Runnable CLI: compare two local revisions, no GitHub
  A single command compares two revisions of a local Git repository
  directly (ticket #111/#154), checks them out, runs the existing
  semantic engine against them unmodified, and prints the same
  plain-text summary format the PR-mode CLI already uses — with no
  GitHub token or Pull Request involved at all.

  Scenario: The summary reports the local Diff's Changes grouped by category
    Given a local repository whose base and head revisions are real git commits differing by a rename
    When the reviewer runs the local Diff summary
    Then the local Diff summary lists the rename Change under category "STRUCTURAL"
    And the local Diff summary reports the rename Change's occurrence and exception counts

  Scenario: The summary reports a mechanical Change under its own category
    Given a local repository whose base and head revisions are real git commits differing by a mechanical replacement
    When the reviewer runs the local Diff summary
    Then the local Diff summary lists the mechanical replacement Change under category "MECHANICAL"

  Scenario: Checkout directories do not survive the run
    Given a local repository whose base and head revisions are real git commits differing by a rename
    When the reviewer runs the local Diff summary
    Then no local Diff checkout directories are left behind

  Scenario: A failed checkout leaves no directories behind either
    Given a local repository whose head revision does not exist
    When the reviewer runs the local Diff summary and it fails
    Then no local Diff checkout directories are left behind
