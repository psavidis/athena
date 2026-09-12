Feature: Post-review AI analysis
  After a reviewer has formed their own understanding of a PR, they can
  optionally trigger a second-stage AI analysis pass over their Review
  Context. The AI returns candidate findings framed as "you may have
  missed X" — never a verdict, never an action the AI takes on its own
  (epic #7 §34, §35, §54).

  Scenario: A reviewer triggers AI analysis after completing their review and receives candidate findings
    Given a PR titled "Move authentication to Account" with a rename Change and a mechanical replacement Change
    And the reviewer has reviewed the rename Change for the review context
    And the reviewer has marked the mechanical replacement Change as mechanical
    And the reviewer assembles the Review Context
    When the reviewer triggers AI analysis
    Then the AI provider returns candidate findings
    And each candidate finding has its own identifier

  Scenario: Triggering AI analysis does not change the reviewer's own review state
    Given a PR titled "Move authentication to Account" with a rename Change and a mechanical replacement Change
    And the reviewer has reviewed the rename Change for the review context
    And the reviewer assembles the Review Context
    When the reviewer triggers AI analysis
    Then the rename Change's review state is still "Reviewed"

  Scenario: AI analysis completes with no findings when the provider has nothing to flag
    Given a PR titled "Move authentication to Account" with a rename Change and a mechanical replacement Change
    And the reviewer has reviewed the rename Change for the review context
    And the reviewer assembles the Review Context
    And the AI provider has nothing to flag for this Review Context
    When the reviewer triggers AI analysis
    Then the AI provider returns no candidate findings
