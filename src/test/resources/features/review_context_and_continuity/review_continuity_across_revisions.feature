Feature: Review continuity across new commits and force-pushes
  When a PR's head revision changes mid-review, a reviewer's prior work
  is not silently discarded: previously-detected Changes are classified
  as unchanged, changed, new, or removed relative to the new revision,
  and review state carries forward for Changes that are still the same
  conceptual transformation (epic #6 §27, §28).

  Scenario: An unchanged Change carries its review state forward
    Given a reviewed rename Change from the prior revision
    And the new revision still contains the same rename, unmodified
    When continuity is computed against the new revision's Changes
    Then the rename Change is classified as unchanged
    And the rename Change's review state carries forward as "Reviewed"

  Scenario: A Change with more occurrences in the new revision is classified as changed
    Given a reviewed mechanical replacement Change from the prior revision with 3 occurrences
    And the new revision has the same mechanical replacement with 5 occurrences
    When continuity is computed against the new revision's Changes
    Then the mechanical replacement Change is classified as changed
    And the mechanical replacement Change's review state resets to "Unseen"

  Scenario: A Change with no prior match is classified as new
    Given a reviewed rename Change from the prior revision
    And the new revision additionally introduces an unrelated move Change
    When continuity is computed against the new revision's Changes
    Then the move Change is classified as new
    And the move Change's review state is "Unseen"

  Scenario: A Change no longer present in the new revision is classified as removed
    Given a reviewed rename Change from the prior revision
    And the new revision no longer contains that rename
    When continuity is computed against the new revision's Changes
    Then the rename Change is classified as removed

  Scenario: Continuity is identity-based, so it survives a force-push that rewrites commit history
    Given a reviewed rename Change from the prior revision
    And the new revision is a force-pushed rewrite that still contains the same rename, unmodified
    When continuity is computed against the new revision's Changes
    Then the rename Change is classified as unchanged
    And the rename Change's review state carries forward as "Reviewed"
