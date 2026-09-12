Feature: Import Pull Request content
  Once a Pull Request is selected, Athena imports its core content so the
  rest of the product has the data it needs to build a review on top of.

  Scenario: Importing PR metadata and base/head revisions
    Given a valid GitHub Personal Access Token
    And repository "octocat/Hello-World" has pull request 42 "Fix typo" authored by "octocat" based on revision "base123" and headed at revision "head456"
    When Athena imports pull request 42 from repository "octocat/Hello-World"
    Then the imported pull request has title "Fix typo"
    And the imported pull request has author "octocat"
    And the imported pull request has base revision "base123"
    And the imported pull request has head revision "head456"

  Scenario: Importing the commit list
    Given a valid GitHub Personal Access Token
    And repository "octocat/Hello-World" has pull request 42 "Fix typo" based on revision "base123" and headed at revision "head456"
    And pull request 42 has commits "abc111" "Fix typo in README" and "def222" "Update README further"
    When Athena imports pull request 42 from repository "octocat/Hello-World"
    Then the imported pull request has 2 commits
    And the imported pull request includes commit "abc111" "Fix typo in README"

  Scenario: Importing changed files with their status
    Given a valid GitHub Personal Access Token
    And repository "octocat/Hello-World" has pull request 42 "Fix typo" based on revision "base123" and headed at revision "head456"
    And pull request 42 changed file "README.md" with status "modified"
    And pull request 42 changed file "docs/NEW.md" with status "added"
    When Athena imports pull request 42 from repository "octocat/Hello-World"
    Then the imported pull request lists changed file "README.md" with status "modified"
    And the imported pull request lists changed file "docs/NEW.md" with status "added"

  Scenario: Importing a file's textual diff
    Given a valid GitHub Personal Access Token
    And repository "octocat/Hello-World" has pull request 42 "Fix typo" based on revision "base123" and headed at revision "head456"
    And pull request 42 changed file "README.md" with diff "@@ -1 +1 @@\n-Helo\n+Hello"
    When Athena imports pull request 42 from repository "octocat/Hello-World"
    Then the imported pull request's diff for "README.md" contains "+Hello"

  Scenario: Importing a file with no available textual diff
    Given a valid GitHub Personal Access Token
    And repository "octocat/Hello-World" has pull request 42 "Fix typo" based on revision "base123" and headed at revision "head456"
    And pull request 42 changed file "logo.png" with status "modified" and no available diff
    When Athena imports pull request 42 from repository "octocat/Hello-World"
    Then the imported pull request marks "logo.png" as having no available diff
