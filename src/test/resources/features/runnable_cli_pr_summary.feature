Feature: Runnable CLI: review a real PR end-to-end
  A single command imports a real PR's metadata, checks out its base and
  head revisions, runs the existing semantic engine against them
  unmodified, and prints a plain-text summary — proving the pipeline
  works end to end against a real PR rather than only in isolation.

  Scenario: The summary reports the PR title and its Changes grouped by category
    Given a PR whose base and head revisions are real git commits differing by a rename
    When the reviewer runs the PR review summary
    Then the summary includes the PR's title
    And the summary lists the rename Change under category "STRUCTURAL"
    And the summary reports the rename Change's occurrence and exception counts

  Scenario: The summary reports a mechanical Change under its own category
    Given a PR whose base and head revisions are real git commits differing by a mechanical replacement
    When the reviewer runs the PR review summary
    Then the summary lists the mechanical replacement Change under category "MECHANICAL"

  Scenario: Checkout directories do not survive the run
    Given a PR whose base and head revisions are real git commits differing by a rename
    When the reviewer runs the PR review summary
    Then no checkout directories are left behind

  Scenario: The GitHub credential helper answers git's authentication prompts without exposing the token on any command line
    Given a GitHub Personal Access Token "ghp_example_token_value"
    When the git credential helper environment is built for that token
    Then invoking the helper script with a Username prompt answers "x-access-token"
    And invoking the helper script with a Password prompt answers the token
