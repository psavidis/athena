Feature: GitHub authentication
  Athena connects to GitHub using a Personal Access Token (PAT) so that
  later import/export capabilities have an authenticated identity to act as.

  Scenario: A valid Personal Access Token identifies the authenticated account
    Given a valid GitHub Personal Access Token
    When Athena connects to GitHub with that token
    Then the connection succeeds
    And Athena reports the authenticated GitHub account's username

  Scenario: An invalid Personal Access Token is rejected clearly
    Given an invalid GitHub Personal Access Token
    When Athena connects to GitHub with that token
    Then the connection fails
    And Athena reports that the token was rejected
