Feature: Per-Change review state and semantic review coverage
  A reviewer needs to mark each Change's review state explicitly and see
  their coverage in terms of meaningful Changes, not files — including
  telling apart "I inspected everything" from "I understood this as a
  mechanical transformation" (epic #4 §18-20).

  Scenario: A newly-produced Change starts Unseen
    Given a newly-produced Change representing a rename
    Then the Change's review state is "Unseen"

  Scenario: A reviewer transitions a Change through review states
    Given a newly-produced Change representing a rename
    When the reviewer marks the Change as "Understanding"
    Then the Change's review state is "Understanding"
    When the reviewer marks the Change as "Reviewed"
    Then the Change's review state is "Reviewed"

  Scenario: A reviewer marks a Change with many occurrences as reviewed in one action
    Given a Change representing a mechanical replacement with 1000 occurrences
    When the reviewer marks the Change as "Reviewed"
    Then the Change's review state is "Reviewed"

  Scenario: Marking a Change mechanical is distinct from marking it reviewed
    Given a newly-produced Change representing a mechanical replacement
    When the reviewer marks the Change as mechanical
    Then the Change is marked mechanical
    And the Change's review state is not "Reviewed"

  Scenario: Review coverage is reported per category
    Given a Change representing a rename with review state "Reviewed"
    And a Change representing a move with review state "Unseen"
    When the reviewer requests review coverage
    Then the coverage report shows "Structural" at 50 percent

  Scenario: Unreviewed Changes can be listed
    Given a Change representing a rename with review state "Reviewed"
    And a Change representing a move with review state "Unseen"
    And a Change representing an extraction with review state "Concern"
    When the reviewer asks which meaningful Changes are unreviewed
    Then the unreviewed list includes the move Change
    And the unreviewed list includes the extraction Change
    And the unreviewed list does not include the rename Change

  Scenario: A Change marked mechanical counts toward coverage without individual inspection
    Given a Change representing a mechanical replacement marked mechanical
    When the reviewer requests review coverage
    Then the coverage report shows "Mechanical" at 100 percent
