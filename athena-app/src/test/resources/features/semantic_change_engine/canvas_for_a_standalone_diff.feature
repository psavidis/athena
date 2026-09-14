Feature: The Semantic Canvas renders for a standalone Diff
  Once a user has created a standalone Diff (ticket #111/#152, no GitHub
  Pull Request involved), the same Semantic Canvas a PR Review lands on
  must actually render for it (ticket #153) — its territory map, semantic
  profile, Change detail, and canvas comments, without requiring a
  GitHub connection.

  Scenario: The territory map renders for a selected standalone Diff
    Given the user has created a standalone Diff whose revisions differ by a renamed method
    When the user requests the Semantic Canvas territory map
    Then the territory map reflects that Diff's detected Changes

  Scenario: The semantic profile renders for a selected standalone Diff
    Given the user has created a standalone Diff whose revisions differ by a renamed method
    When the user requests the semantic profile for that Diff's touched module
    Then the semantic profile reflects that Diff's detected Changes

  Scenario: A Change's detail view renders for a selected standalone Diff
    Given the user has created a standalone Diff whose revisions differ by a renamed method
    When the user requests that Diff's Change detail
    Then the Change detail is returned

  Scenario: A user can comment on a canvas item while viewing a standalone Diff
    Given the user has created a standalone Diff whose revisions differ by a renamed method
    When the user posts a comment on a canvas item in that Diff
    Then the comment appears in that item's comment thread

  Scenario: The Semantic Canvas is reachable for a standalone Diff without a GitHub connection
    Given the user is not connected to GitHub
    And the user has created a standalone Diff whose revisions differ by a renamed method
    When the user requests the Semantic Canvas territory map
    Then the territory map reflects that Diff's detected Changes
