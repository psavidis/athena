Feature: Semantic Change Explorer — Guided Review mode and Diff/Explorer toggle
  Guided Review groups the Semantic Change Explorer's seven levels (ticket
  #95's shell) into a fixed sequence of review chapters, so a reviewer can
  walk a large change in a natural order instead of picking levels off the
  spine themselves (ticket #91 §14). A Diff view / Semantic Explorer toggle
  keeps the traditional flat diff reachable alongside the Explorer, since
  the diff remains the underlying evidence even though it is no longer the
  primary navigation model (ticket #91 §1).

  Scenario: Starting Guided Review shows the first chapter
    Given the reviewer is viewing the Semantic Change Explorer for a Change
    When the reviewer starts guided review
    Then the reviewer sees the "Understand the change" chapter
    And that chapter shows the Structure level's content

  Scenario: Continuing advances through the chapters in the fixed order
    Given the reviewer is in guided review, viewing the "Understand the change" chapter
    When the reviewer continues
    Then the reviewer sees the "Understand the implementation" chapter
    And that chapter shows the Pattern and Framework levels' content together

  Scenario: A chapter pairing two levels shows both levels' content together
    Given the reviewer is in guided review, viewing the "Understand the affected behavior" chapter for a Change whose Capability level is classified as "Billing" and whose Flow level is classified as "Checkout"
    Then the chapter shows the Capability level's content for "Billing"
    And the chapter shows the Flow level's content for "Checkout"

  Scenario: Going back returns to the previous chapter
    Given the reviewer is in guided review, viewing the "Understand the implementation" chapter
    When the reviewer goes back
    Then the reviewer sees the "Understand the change" chapter

  Scenario: The chapter rail shows the reviewer's current position and total chapter count
    Given the reviewer is in guided review, viewing the "Understand the affected behavior" chapter
    Then the chapter rail shows the reviewer is on chapter 3 of 6

  Scenario: Continuing past the last chapter is not possible
    Given the reviewer is in guided review, viewing the last chapter, "Inspect the evidence"
    Then no continue action is offered

  Scenario: Going back from the first chapter is not possible
    Given the reviewer is in guided review, viewing the first chapter, "Understand the change"
    Then no back action is offered

  Scenario: Exiting guided review returns to the normal Explorer at the reviewer's prior position
    Given the reviewer was viewing the Pattern level of the Semantic Change Explorer before starting guided review
    And the reviewer is in guided review, viewing the "Understand the affected behavior" chapter
    When the reviewer exits guided review
    Then the reviewer sees the normal Explorer, one level at a time
    And the Pattern level is indicated as the current level

  Scenario: A reviewer switches from the diff view to the Semantic Explorer
    Given the reviewer is viewing the diff view for a Change
    When the reviewer switches to the Semantic Explorer
    Then the reviewer sees the Semantic Change Explorer for that same Change

  Scenario: A reviewer switches from the Semantic Explorer back to the diff view
    Given the reviewer is viewing the Semantic Change Explorer for a Change
    When the reviewer switches to the diff view
    Then the reviewer sees the diff view for that same Change

  Scenario: Switching modes does not lose the selected pull request
    Given the reviewer has selected a pull request and is viewing the diff view for one of its Changes
    When the reviewer switches to the Semantic Explorer and back to the diff view
    Then the reviewer is still viewing that same pull request
