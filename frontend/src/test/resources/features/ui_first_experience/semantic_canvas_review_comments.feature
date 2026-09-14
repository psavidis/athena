Feature: Semantic Canvas — review comments (pins, thread, add/edit/delete)
  A reviewer adds, sees, edits, and deletes review comments directly on
  the canvas item they're about (ticket #134), so a comment's location is
  always the item itself — never a separate list disconnected from the
  canvas.

  Scenario: A canvas item with comments shows a pin badge
    Given the reviewer is viewing a territory whose "PaymentValidator.java" file node has 2 comments
    Then the "PaymentValidator.java" file node shows a comment pin badge with count "2"

  Scenario: A canvas item with no comments shows no pin badge
    Given the reviewer is viewing a territory whose "PaymentValidator.java" file node has no comments
    Then the "PaymentValidator.java" file node shows no comment pin badge

  Scenario: Opening a pin shows the item's comment thread
    Given the reviewer is viewing a territory whose "PaymentValidator.java" file node has a comment from "alex" posted "2 hours ago"
    When the reviewer clicks the comment pin on "PaymentValidator.java"
    Then the detail drawer opens showing the comment thread for "PaymentValidator.java"
    And the thread shows a comment by "alex" posted "2 hours ago"

  Scenario: An item with no comments yet still offers a way to start a thread
    Given the reviewer is viewing a territory whose "PaymentValidator.java" file node has no comments
    When the reviewer opens the comment thread for "PaymentValidator.java"
    Then the drawer shows that there are no comments yet on this item
    And the drawer shows a way to post the first comment

  Scenario: Posting a new comment adds it to the thread and updates the pin badge
    Given the reviewer has the comment thread open for "PaymentValidator.java", which has no comments yet
    When the reviewer posts the comment "Should this handle the null case?"
    Then the thread shows a new comment with the text "Should this handle the null case?"
    And the "PaymentValidator.java" file node shows a comment pin badge with count "1"

  Scenario: Posting a blank comment is blocked
    Given the reviewer has the comment thread open for "PaymentValidator.java"
    When the reviewer tries to post a blank comment
    Then no new comment is added to the thread

  Scenario: Editing an existing comment updates its text in place
    Given the reviewer has posted the comment "Should this handle the null case?" on "PaymentValidator.java"
    When the reviewer edits that comment to "Should this handle the null case? Never mind, it's covered."
    Then the thread shows the comment as "Should this handle the null case? Never mind, it's covered."
    And the thread shows only one comment from the reviewer on "PaymentValidator.java"

  Scenario: Canceling an edit restores the original comment text
    Given the reviewer has posted the comment "Should this handle the null case?" on "PaymentValidator.java"
    When the reviewer starts editing that comment, changes the text, then cancels the edit
    Then the thread still shows the comment as "Should this handle the null case?"

  Scenario: Deleting a comment removes it and updates the pin badge
    Given the reviewer has posted the only comment on "PaymentValidator.java"
    When the reviewer deletes that comment
    Then the thread no longer shows that comment
    And the "PaymentValidator.java" file node shows no comment pin badge

  Scenario: The topbar shows the total comment count across the PR
    Given the PR under review has 5 comments across its canvas items
    Then the topbar shows "5 comments"

  Scenario: Toggling "show only commented" dims uncommented items without removing them
    Given the reviewer is viewing a territory where "PaymentValidator.java" has a comment and "OrderService.java" has none
    When the reviewer toggles the "show only commented" filter on
    Then "OrderService.java" is visually de-emphasized but still visible on the canvas
    And "PaymentValidator.java" is not de-emphasized

  Scenario: Turning "show only commented" back off restores normal emphasis
    Given the reviewer has the "show only commented" filter toggled on
    When the reviewer toggles the filter off
    Then no canvas item is de-emphasized because of the comment filter
