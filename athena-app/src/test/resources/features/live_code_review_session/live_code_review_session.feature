Feature: Live Code Review Session lifecycle
  A reviewer starts a Live Code Review Session from a review they already
  have open, so other reviewers can join the same session via a shareable
  link and see who else is present — without re-running the review's
  analysis (ticket #158).

  Scenario: A reviewer starts a Live Code Review Session from their current review
    Given a reviewer has PR 42 in "acme/widgets" selected
    When the reviewer starts a Live Code Review Session as "Petros"
    Then the session exists
    And "Petros" appears as a participant in the session
    And "Petros" is the session's presenter

  Scenario: Starting a session with no review selected is rejected
    Given a reviewer has no PR or Diff selected
    When the reviewer attempts to start a Live Code Review Session as "Petros"
    Then the live session request is rejected as invalid

  Scenario: A reviewer starts a session from a standalone Diff, with no GitHub PR at all
    Given a reviewer has a standalone Diff selected, with no GitHub PR
    When the reviewer starts a Live Code Review Session as "Petros"
    Then the session exists
    And "Petros" is the session's presenter

  Scenario: A second reviewer joins the session via its shareable id
    Given "Petros" has started a Live Code Review Session for PR 42 in "acme/widgets"
    When "Maria" joins that session
    Then "Maria" appears as a participant in the session
    And "Petros" still appears as a participant in the session

  Scenario: A newly joined participant immediately receives the current session state
    Given "Petros" has started a Live Code Review Session for PR 42 in "acme/widgets"
    When "Maria" joins that session
    Then the state "Maria" receives on joining shows "Petros" as the presenter
    And the state "Maria" receives on joining lists both "Petros" and "Maria" as participants

  Scenario: Joining an unknown session is rejected
    When a reviewer attempts to join session "does-not-exist"
    Then the live session request is rejected as invalid

  Scenario: A participant who leaves no longer appears as an active participant
    Given "Petros" has started a Live Code Review Session for PR 42 in "acme/widgets"
    And "Maria" has joined that session
    When "Maria" leaves the session
    Then "Maria" no longer appears as an active participant in the session
    And "Petros" still appears as a participant in the session

  Scenario: A participant who disconnects no longer appears as active, but can reconnect
    Given "Petros" has started a Live Code Review Session for PR 42 in "acme/widgets"
    And "Maria" has joined that session
    When "Maria" disconnects from the session
    Then "Maria" no longer appears as an active participant in the session
    When "Maria" reconnects to the session
    Then "Maria" appears as an active participant in the session
