Feature: Comment and private-note storage with GitHub-sync exclusion
  Comments and private notes are stored with a first-class distinction
  between them, so that syncing a review to GitHub can only ever read
  from the comment side — a private note must never be included in any
  GitHub sync operation, even one that tries to serialize everything
  attached to a Change (epic #6 §21, §22).

  Scenario: A comment at review scope syncs to GitHub as a general comment
    Given annotation sync targets repository "octocat/hello-world" pull request 42, which accepts synced comments
    And a comment "Overall this looks good" at review scope
    When the stored comments are synced to the pull request
    Then the pull request has a synced general comment "Overall this looks good"
    And the sync result reports every comment synced successfully

  Scenario: A comment at line scope syncs to GitHub as a line comment
    Given annotation sync targets repository "octocat/hello-world" pull request 42, which accepts synced comments
    And a comment "Why null here?" at line 12 of file "Greeter.java"
    When the stored comments are synced to the pull request
    Then the pull request has a synced line comment "Why null here?" on file "Greeter.java" line 12

  Scenario: A private note is never synced to GitHub, even alongside a comment at the same scope
    Given annotation sync targets repository "octocat/hello-world" pull request 42, which accepts synced comments
    And a comment "Nice cleanup" at review scope
    And a private note "Not fully sure about this, ask the author" at review scope
    When the stored comments are synced to the pull request
    Then the pull request has a synced general comment "Nice cleanup"
    And the pull request has no synced comment mentioning "ask the author"

  Scenario: Syncing when only private notes exist sends nothing to GitHub
    Given annotation sync targets repository "octocat/hello-world" pull request 42, which accepts synced comments
    And a private note "Just thinking out loud here" at review scope
    When the stored comments are synced to the pull request
    Then the pull request has no synced general comments
