Feature: Change Map & PR Understanding View rendering (frontend)
  Once a reviewer has selected a PR, the browser fetches its Change Map
  and renders the PR Understanding summary and the list of Changes,
  without the reviewer ever reading a raw diff (ticket #80).

  Scenario: The PR Understanding summary shows the PR title and a count per category
    Given the reviewer has selected a PR titled "Move authentication to Account"
    And its Change Map has 2 Behavioral, 1 Structural, 3 Mechanical, and 0 Unknown Changes
    When the reviewer views the Change Map page
    Then the PR Understanding summary shows the title "Move authentication to Account"
    And the summary shows 2 Behavioral, 1 Structural, 3 Mechanical, and 0 Unknown Changes

  Scenario: The Change Map lists every Change with its description, category, and review state
    Given the reviewer has selected a PR whose Change Map contains a Change described as "Rename greet to salute" categorized as Behavioral
    When the reviewer views the Change Map page
    Then the Change Map shows a Change described as "Rename greet to salute"
    And that Change is shown categorized as Behavioral
    And that Change is shown with review state Unseen

  Scenario: An empty Change Map renders the summary with all-zero counts and no Changes listed
    Given the reviewer has selected a PR titled "No-op PR" with no detected Changes
    When the reviewer views the Change Map page
    Then the PR Understanding summary shows the title "No-op PR"
    And the summary shows 0 Behavioral, 0 Structural, 0 Mechanical, and 0 Unknown Changes
    And the Change Map lists no Changes

  Scenario: Viewing the Change Map while not connected to GitHub routes back to the connect step
    Given the reviewer is not connected to GitHub
    When the reviewer views the Change Map page
    Then the reviewer is routed back to the connect step

  Scenario: Viewing the Change Map with no PR selected routes back to PR selection
    Given the reviewer is connected to GitHub but has not selected a PR
    When the reviewer views the Change Map page
    Then the reviewer is routed back to PR selection
