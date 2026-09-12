Feature: Per-Change review state display and explicit review completion
  A reviewer sees and changes each Change's review state from the Change
  Map/detail view, sees semantic review coverage, and must explicitly
  confirm completing the review — the system never auto-completes a
  review just because every Change reached a terminal state
  (epic #5 §18, §20, §40, §41).

  Scenario: The Change Map reflects a review-state transition
    Given a reviewable PR with a rename Change
    When the reviewer transitions that Change to "Understanding"
    Then the Change Map shows that Change's review state as "Understanding"

  Scenario: Semantic review coverage is reported as N of M Changes
    Given a reviewable PR with a rename Change and a mechanical replacement Change
    And the reviewer has reviewed the rename Change
    When the reviewer requests the review coverage summary
    Then the summary reports exactly 1 Change reviewed out of the total detected

  Scenario: Completing a review requires explicit confirmation even when every Change is reviewed
    Given a reviewable PR with a rename Change
    And the reviewer has reviewed every Change
    When the reviewer attempts to finish the review without confirming
    Then the review is not marked complete

  Scenario: An explicitly confirmed review completion succeeds
    Given a reviewable PR with a rename Change
    And the reviewer has reviewed every Change
    When the reviewer confirms completing the review
    Then the review is marked complete

  Scenario: Reaching full coverage does not itself complete the review
    Given a reviewable PR with a rename Change
    When the reviewer transitions that Change to "Reviewed"
    Then the review is not marked complete
