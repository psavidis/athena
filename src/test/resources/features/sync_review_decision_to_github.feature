Feature: Sync review decision to GitHub
  A reviewer's decision made in Athena — approve or request changes —
  synchronizes to the real GitHub Pull Request as a native review.

  Scenario: Syncing an approval
    Given a valid GitHub Personal Access Token
    And repository "octocat/Hello-World" pull request 42 accepts synced reviews
    When Athena syncs an approval "Looks great" to pull request 42 in repository "octocat/Hello-World"
    Then the review sync succeeds
    And GitHub shows a "APPROVE" review "Looks great" on pull request 42

  Scenario: Syncing a request for changes
    Given a valid GitHub Personal Access Token
    And repository "octocat/Hello-World" pull request 42 accepts synced reviews
    When Athena syncs a request for changes "Please address the comments" to pull request 42 in repository "octocat/Hello-World"
    Then the review sync succeeds
    And GitHub shows a "REQUEST_CHANGES" review "Please address the comments" on pull request 42

  Scenario: Review sync failure is surfaced clearly
    Given an invalid GitHub Personal Access Token
    When Athena syncs an approval "Looks great" to pull request 42 in repository "octocat/Hello-World"
    Then the review sync fails clearly
