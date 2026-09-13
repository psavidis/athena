Feature: Select repository and Pull Request
  After connecting a GitHub account, a user selects which repository and
  which Pull Request Athena should work with.

  Scenario: Listing accessible repositories
    Given a valid GitHub Personal Access Token
    And the account has access to repositories "octocat/Hello-World" and "octocat/Spoon-Knife"
    When Athena lists the accessible repositories
    Then the repository list includes "octocat/Hello-World" and "octocat/Spoon-Knife"

  Scenario: Listing open Pull Requests for a selected repository
    Given a valid GitHub Personal Access Token
    And repository "octocat/Hello-World" has open pull request 42 "Fix typo"
    When Athena lists open pull requests for repository "octocat/Hello-World"
    Then the pull request list includes pull request 42 "Fix typo"

  Scenario: Selecting a specific Pull Request
    Given a valid GitHub Personal Access Token
    And repository "octocat/Hello-World" has open pull request 42 "Fix typo"
    When Athena selects pull request 42 in repository "octocat/Hello-World"
    Then the selected pull request is 42 "Fix typo"

  Scenario: Selecting a Pull Request that does not exist
    Given a valid GitHub Personal Access Token
    And repository "octocat/Hello-World" has no pull request 999
    When Athena selects pull request 999 in repository "octocat/Hello-World"
    Then the selection fails clearly

  Scenario: Listing repositories with an invalid token
    Given an invalid GitHub Personal Access Token
    When Athena lists the accessible repositories
    Then the listing fails clearly
