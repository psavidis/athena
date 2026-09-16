Feature: Review Recording control
  A developer starts and stops a Review Recording directly from the
  review they're already viewing, seeing a disclosure of what will be
  captured first and a subtle persistent indicator while it's active
  (ticket #203).

  Scenario: A developer sees the disclosure before starting a recording
    Given a developer is viewing a review with the Review Recording control
    When the developer opens the Start Review Recording action
    Then the disclosure explains what will be captured

  Scenario: Acknowledging the disclosure starts the recording and shows the indicator
    Given a developer is viewing a review with the Review Recording control
    When the developer starts the recording, acknowledging the disclosure
    Then the recording indicator is shown with elapsed time and participant count

  Scenario: A developer stops an active recording
    Given a developer has an active Review Recording
    When the developer stops the recording
    Then the recording indicator is no longer shown

  Scenario: A developer tags the current moment while recording
    Given a developer has an active Review Recording
    When the developer tags the current moment as a question
    Then the moment is tagged as a question

  Scenario: A developer confirms a pending moment
    Given a developer has tagged the current moment as a question
    When the developer confirms the pending moment
    Then the moment appears confirmed on the timeline

  Scenario: A developer rejects a pending moment
    Given a developer has tagged the current moment as a question
    When the developer rejects the pending moment
    Then the pending-moment confirmation prompt is dismissed

  Scenario: Stopping a recording shows a duration and moment-count summary
    Given a developer has confirmed a tagged moment
    When the developer stops the recording
    Then the review summary shows the recording's duration and moment counts
