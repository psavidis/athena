Feature: Sync comments to GitHub
  Comments a user writes in Athena synchronize to the real GitHub Pull
  Request, so anyone using GitHub directly sees the feedback.

  Scenario: Syncing a general PR-scoped comment
    Given a valid GitHub Personal Access Token
    And repository "octocat/Hello-World" pull request 42 accepts synced comments
    When Athena syncs a general comment "Looks good overall" to pull request 42 in repository "octocat/Hello-World"
    Then the sync succeeds
    And GitHub shows a general comment "Looks good overall" on pull request 42

  Scenario: Syncing a line-scoped review comment
    Given a valid GitHub Personal Access Token
    And repository "octocat/Hello-World" pull request 42 is at commit "head456" and accepts synced comments
    When Athena syncs a line comment "Please rename this variable" on file "README.md" line 10 to pull request 42 in repository "octocat/Hello-World"
    Then the sync succeeds
    And GitHub shows a line comment "Please rename this variable" on file "README.md" line 10 of pull request 42

  Scenario: Sync failure is surfaced clearly
    Given an invalid GitHub Personal Access Token
    When Athena syncs a general comment "Looks good overall" to pull request 42 in repository "octocat/Hello-World"
    Then the sync fails clearly

  Scenario: Syncing multiple comments from the same review does not overwrite or merge them
    Given a valid GitHub Personal Access Token
    And repository "octocat/Hello-World" pull request 42 is at commit "head456" and accepts synced comments
    When Athena syncs a general comment "Looks good overall" to pull request 42 in repository "octocat/Hello-World"
    And Athena syncs a line comment "Please rename this variable" on file "README.md" line 10 to pull request 42 in repository "octocat/Hello-World"
    Then GitHub shows a general comment "Looks good overall" on pull request 42
    And GitHub shows a line comment "Please rename this variable" on file "README.md" line 10 of pull request 42

  Scenario: Retrying a general comment sync does not create a duplicate
    Given a valid GitHub Personal Access Token
    And repository "octocat/Hello-World" pull request 42 accepts synced comments
    When Athena syncs a general comment "Looks good overall" to pull request 42 in repository "octocat/Hello-World" twice
    Then GitHub shows exactly one general comment "Looks good overall" on pull request 42

  Scenario: Retrying a line comment sync does not create a duplicate
    Given a valid GitHub Personal Access Token
    And repository "octocat/Hello-World" pull request 42 is at commit "head456" and accepts synced comments
    When Athena syncs a line comment "Please rename this variable" on file "README.md" line 10 to pull request 42 in repository "octocat/Hello-World" twice
    Then GitHub shows exactly one line comment "Please rename this variable" on file "README.md" line 10 of pull request 42
