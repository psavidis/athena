Feature: Live Code Review Session panel on the Semantic Canvas
  A reviewer can start a Live Code Review Session directly from the
  Semantic Canvas they're already viewing, see who else has joined, and
  discuss the review together — without leaving the canvas for a separate
  collaboration screen (ticket #158).

  Scenario: Starting a session shows the reviewer as the sole, presenting participant
    Given a reviewer is viewing the Semantic Canvas for a selected PR
    When the reviewer starts a Live Code Review Session as "Petros"
    Then the canvas shows "Petros" as a participant
    And the canvas shows a way to copy the session's shareable link

  Scenario: A reviewer posts a session comment and sees it listed
    Given a reviewer has started a Live Code Review Session
    When the reviewer opens the session comments and posts "Looks good"
    Then the session comments show "Looks good"
