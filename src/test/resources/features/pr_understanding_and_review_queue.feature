Feature: PR Understanding View and Review Queue
  Before inspecting individual Changes, a reviewer sees a high-level PR
  Understanding View establishing a mental model, then a Review Queue
  suggesting — never dictating — an order to look at Changes in
  (epic #5 §16, §17).

  Scenario: The PR Understanding View summarizes scope by category
    Given a PR titled "Refactor authentication" with a rename Change, a mechanical replacement Change, and a formatting-only Change
    When the reviewer opens the PR Understanding View
    Then the view shows the PR title "Refactor authentication"
    And the view shows at least 1 Structural Change
    And the view shows at least 2 Mechanical Changes

  Scenario: The Review Queue orders behavioral-shaped Changes before purely mechanical ones
    Given a PR with a mechanical replacement Change and a rename Change
    When the reviewer opens the Review Queue
    Then the queue lists the rename Change before the mechanical replacement Change

  Scenario: A reviewer can pick any Change out of queue order with no penalty
    Given a PR with a mechanical replacement Change and a rename Change
    When the reviewer opens the Review Queue
    And the reviewer selects the mechanical replacement Change out of order
    Then the selection succeeds
    And the Review Queue is unchanged

  Scenario: An empty PR produces an empty understanding view and queue, not an error
    Given a PR titled "No-op PR" with no detected Changes
    When the reviewer opens the PR Understanding View
    Then the view shows the PR title "No-op PR"
    And the view shows at least 0 Structural Change
