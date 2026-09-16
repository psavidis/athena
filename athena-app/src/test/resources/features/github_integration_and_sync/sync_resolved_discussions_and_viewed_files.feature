Feature: Sync resolved discussions and viewed-file state to GitHub
  A reviewer's progress in Athena — a discussion thread marked resolved, a
  file marked viewed — synchronizes to GitHub wherever its API supports it.

  Scenario: Resolving a discussion thread
    Given a valid GitHub Personal Access Token
    And review thread "thread-1" on pull request 42 in repository "octocat/Hello-World" can be resolved
    When Athena resolves discussion thread "thread-1" on pull request 42 in repository "octocat/Hello-World"
    Then the resolve sync succeeds
    And GitHub shows review thread "thread-1" as resolved

  Scenario: Marking a file as viewed
    Given a valid GitHub Personal Access Token
    And pull request 42 in repository "octocat/Hello-World" accepts viewed-file sync for "README.md"
    When Athena marks file "README.md" as viewed on pull request 42 in repository "octocat/Hello-World"
    Then the viewed-file sync succeeds
    And GitHub shows file "README.md" as viewed on pull request 42

  Scenario: Resolving a discussion thread that GitHub cannot resolve
    Given a valid GitHub Personal Access Token
    And review thread "unknown-thread" cannot be resolved
    When Athena resolves discussion thread "unknown-thread" on pull request 42 in repository "octocat/Hello-World"
    Then the resolve sync fails clearly

  Scenario: Marking a file as viewed with an invalid token
    Given an invalid GitHub Personal Access Token
    When Athena marks file "README.md" as viewed on pull request 42 in repository "octocat/Hello-World"
    Then the viewed-file sync fails clearly

  Scenario: Retrying a thread resolution sends only one mutation to GitHub
    Given a valid GitHub Personal Access Token
    And review thread "thread-1" on pull request 42 in repository "octocat/Hello-World" can be resolved
    When Athena resolves discussion thread "thread-1" on pull request 42 in repository "octocat/Hello-World" twice
    Then GitHub shows review thread "thread-1" as resolved
    And GitHub received exactly one resolve mutation for review thread "thread-1"

  Scenario: Retrying a mark-as-viewed sync sends only one mutation to GitHub
    Given a valid GitHub Personal Access Token
    And pull request 42 in repository "octocat/Hello-World" accepts viewed-file sync for "README.md"
    When Athena marks file "README.md" as viewed on pull request 42 in repository "octocat/Hello-World" twice
    Then GitHub shows file "README.md" as viewed on pull request 42
    And GitHub received exactly one mark-as-viewed mutation for file "README.md" on pull request 42
