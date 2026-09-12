Feature: AI findings review (independent accept/dismiss)
  Each finding an AI analysis pass returns stays a suggestion the reviewer
  evaluates on its own — never a verdict passively accepted, and never
  something that acts on the reviewer's behalf (epic #7 §35).

  Scenario: Each finding is displayed as its own item, clearly AI-attributed
    Given the reviewer has two AI findings, one about the rename Change and one with no related Change
    Then each finding item is attributed as "AI"

  Scenario: Accepting one finding does not affect another finding's disposition
    Given the reviewer has two AI findings, one about the rename Change and one with no related Change
    When the reviewer accepts the finding about the rename Change
    Then that finding's disposition is "Accepted"
    And the other finding's disposition is still "Pending"

  Scenario: Dismissing one finding does not affect another finding's disposition
    Given the reviewer has two AI findings, one about the rename Change and one with no related Change
    When the reviewer dismisses the finding about the rename Change
    Then that finding's disposition is "Dismissed"
    And the other finding's disposition is still "Pending"

  Scenario: A finding about a specific Change offers a jump target to that Change's detail view
    Given the reviewer has two AI findings, one about the rename Change and one with no related Change
    Then the finding about the rename Change has a jump target to the rename Change's detail view
    And the finding with no related Change has no jump target

  Scenario: Accepting a finding never changes the underlying Change's review state
    Given the reviewer has two AI findings, one about the rename Change and one with no related Change
    And the rename Change's review state is "Reviewed"
    When the reviewer accepts the finding about the rename Change
    Then the rename Change's review state is still "Reviewed"
