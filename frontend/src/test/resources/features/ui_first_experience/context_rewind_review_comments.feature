Feature: Context Rewind — Pull Request review comments
  A Pull Request referenced in Context Rewind (ticket #192) can be opened
  to see its existing review comments and overall review verdicts,
  grouped by file.

  Re-scoped during spec-writing (flagged to and confirmed with the user):
  the original vision was a structured "intent → architecture → question
  → discussion → decision → outcome" reasoning trail, with a selectable
  question revealing its own discussion, answer, and resulting code
  change. The real review data Athena can import
  (`com.athena.github.ReviewComment`/`Review`) is a flat, unordered list —
  author, body, and file path only, with no reply-threading, no
  timestamps, no diff position, and no way to distinguish a "question"
  comment from an "answer" or to correlate any comment to a resulting
  code change. This ticket instead shows that same real data as a flat,
  file-grouped list plus each reviewer's overall verdict — not the
  structured drill-down the original vision described.

  Scenario: Opening a Pull Request's review shows its comments grouped by file
    Given Pull Request 217 has a review comment by "octocat" on "PaymentProcessor.java" saying "Can this produce duplicate charges?"
    And Pull Request 217 has a review comment by "hubot" on "PaymentProcessor.java" saying "Guarded by the idempotency key."
    When the developer opens the review for Pull Request 217
    Then the review lists both comments under "PaymentProcessor.java"

  Scenario: Opening a Pull Request's review shows each reviewer's overall verdict
    Given "octocat" approved Pull Request 217
    When the developer opens the review for Pull Request 217
    Then the review shows "octocat" approved

  Scenario: A Pull Request with no review comments or reviews says so plainly
    Given Pull Request 300 has no review comments or reviews
    When the developer opens the review for Pull Request 300
    Then the review states that nothing was recorded for it
