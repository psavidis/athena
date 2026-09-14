Feature: Diff entry point on the main page
  Diff is a first-class capability alongside PR Review (ticket #111/#153):
  the main page offers a way to compare two local Git revisions directly,
  without going through GitHub, landing on the same Semantic Canvas a PR
  Review lands on.

  Scenario: The main page offers a Diff entry point alongside the GitHub picker
    Given the user is connected to GitHub
    When the user opens the main page
    Then the main page shows a way to start a Diff
    And the main page still shows the GitHub repository picker

  Scenario: A user not connected to GitHub can still start a Diff
    Given the user is not connected to GitHub
    When the user opens the main page
    Then the main page shows a way to start a Diff

  Scenario: Starting a Diff lands on the Semantic Canvas
    Given the user is on the Diff entry point
    When the user starts a Diff from a local repository path and two revisions
    Then the user lands on the Semantic Canvas showing that Diff's analysis

  Scenario: Starting a Diff with an invalid revision shows an error on the form
    Given the user is on the Diff entry point
    When the user starts a Diff with a revision that does not exist
    Then the Diff entry point shows an error
    And the user remains on the Diff entry point
