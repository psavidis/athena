Feature: Import existing review comments, review state, and permissions
  Before adding anything new, Athena imports a Pull Request's existing
  review comments and review state, plus the authenticated user's
  permissions on the repository, to have a complete picture of where the
  review currently stands.

  Scenario: Importing existing review comments
    Given a valid GitHub Personal Access Token
    And pull request 42 in repository "octocat/Hello-World" has a review comment by "hubot" saying "Please rename this variable" on file "README.md"
    When Athena imports review data for pull request 42 in repository "octocat/Hello-World"
    Then the imported review data includes a comment by "hubot" saying "Please rename this variable" on file "README.md"

  Scenario: Importing review state
    Given a valid GitHub Personal Access Token
    And pull request 42 in repository "octocat/Hello-World" has a review by "hubot" with state "APPROVED"
    And pull request 42 in repository "octocat/Hello-World" has a review by "monalisa" with state "CHANGES_REQUESTED"
    When Athena imports review data for pull request 42 in repository "octocat/Hello-World"
    Then the imported review data shows "hubot" with review state "APPROVED"
    And the imported review data shows "monalisa" with review state "CHANGES_REQUESTED"

  Scenario: Importing the authenticated user's repository permissions
    Given a valid GitHub Personal Access Token
    And the authenticated user has permission "write" on repository "octocat/Hello-World"
    When Athena imports permissions for repository "octocat/Hello-World"
    Then the imported permission level is "write"

  Scenario: Importing review data with an invalid token
    Given an invalid GitHub Personal Access Token
    When Athena imports review data for pull request 42 in repository "octocat/Hello-World"
    Then the import fails clearly
