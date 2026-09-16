Feature: Keyboard commenting
  A reviewer participates fully in a review from the keyboard — creating,
  replying to, editing, resolving, and navigating comments — without ever
  needing to click a specific comment-composer affordance (ticket #159).

  Scenario: Creating a comment without clicking any affordance
    Given the reviewer has keyboard focus on a Change
    When the reviewer uses the keyboard shortcut to create a comment
    Then a comment editor opens
    And keyboard focus moves into the comment editor

  Scenario: Submitting a comment with the keyboard
    Given the reviewer has an open comment editor with the text "Looks good" typed in
    When the reviewer uses the keyboard shortcut to submit the comment
    Then the Change's comments include "Looks good"
    And the comment editor's draft is cleared, ready for another comment

  Scenario: Cancelling comment creation with the keyboard
    Given the reviewer has an open comment editor with the text "Draft thought" typed in
    When the reviewer uses the keyboard shortcut to cancel the comment
    Then the comment editor closes
    And the Change's comments do not include "Draft thought"

  Scenario: Replying to a comment with the keyboard
    Given the reviewer is focused on an existing comment "Why return null here?"
    When the reviewer uses the keyboard shortcut to reply to the focused comment
    And the reviewer types the reply "Legacy API contract" and submits it with the keyboard
    Then "Why return null here?" has a reply "Legacy API contract"

  Scenario: Editing a comment with the keyboard
    Given the reviewer is focused on their own comment "Looks good overall"
    When the reviewer uses the keyboard shortcut to edit the focused comment
    And the reviewer replaces the text with "Looks great overall" and submits it with the keyboard
    Then the comment now reads "Looks great overall"

  Scenario: Resolving a comment with the keyboard
    Given the reviewer is focused on an unresolved comment
    When the reviewer uses the keyboard shortcut to resolve the focused comment
    Then the comment's status is "Resolved"

  Scenario: Unresolving a previously resolved comment with the keyboard
    Given the reviewer is focused on a resolved comment
    When the reviewer uses the keyboard shortcut to unresolve the focused comment
    Then the comment's status is "Unresolved"

  Scenario: Moving to the next comment with the keyboard
    Given the reviewer is focused on a comment and a later comment exists in the review
    When the reviewer uses the keyboard shortcut for the next comment
    Then keyboard focus moves to the next comment

  Scenario: Jumping from a comment to the code it is attached to
    Given the reviewer is focused on a comment attached to line 12 of file "Greeter.java"
    When the reviewer uses the keyboard shortcut to jump to the comment's code
    Then keyboard focus moves to line 12 of file "Greeter.java"

  Scenario: Typing inside the comment editor is not intercepted as a shortcut
    Given the reviewer has an open comment editor
    When the reviewer types a character that is also a keyboard shortcut elsewhere in the review
    Then that character is inserted into the comment text
    And no shortcut action is triggered
