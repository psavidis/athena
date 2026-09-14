Feature: A standalone Diff, without GitHub
  A user can compare two revisions of a local Git repository directly
  (ticket #111/#152) — Diff is a first-class capability independent of a
  GitHub Pull Request, reusing the exact same semantic analysis a PR
  Review uses.

  Scenario: Creating a Diff from two differing local revisions detects Changes
    Given a local repository whose two revisions differ by a renamed method
    When the user creates a Diff from those two revisions
    Then the Diff reports at least one detected Change

  Scenario: Creating a Diff from two identical local revisions detects no Changes
    Given a local repository whose two revisions are identical
    When the user creates a Diff from those two revisions
    Then the Diff reports no detected Changes

  Scenario: Creating a new Diff replaces the previously-selected Diff
    Given the user has already created a Diff from a local repository
    When the user creates a second Diff from a different local repository
    Then the session's selected Diff is the second one

  Scenario: Creating a Diff clears a previously-selected PR Review
    Given the user has a PR Review selected in the session
    When the user creates a Diff from a local repository
    Then the session no longer has a PR Review selected

  Scenario: Selecting a PR Review clears a previously-selected standalone Diff
    Given the user has already created a standalone Diff
    When the user selects a PR Review in the session
    Then the session no longer has a standalone Diff selected
