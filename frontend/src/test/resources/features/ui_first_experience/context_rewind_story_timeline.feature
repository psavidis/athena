Feature: Context Rewind — Story Timeline & Evidence Panel
  Invoking Context Rewind on an entity (ticket #187, the foundational view
  for ticket #162's UX) opens a visual timeline of the semantically
  meaningful events that shaped its current state — not a wall of
  AI-generated prose. Selecting an event reveals the evidence behind it.
  Built on top of the backend context reconstruction already delivered in
  ticket #161.

  Scenario: Opening Context Rewind shows an entity's evolution timeline, oldest to newest
    Given a pull request is selected whose repository's history for "PaymentProcessor" includes a "2025-12-01" change and a "2026-02-01" change
    When the developer opens Context Rewind for "PaymentProcessor"
    Then the timeline lists the "2025-12-01" event before the "2026-02-01" event
    And the timeline ends at "PaymentProcessor"'s current state

  Scenario: Selecting a timeline event reveals the evidence behind it
    Given the developer has Context Rewind open for "PaymentProcessor" showing a "Retry mechanism added" event
    When the developer selects the "Retry mechanism added" event
    Then the evidence panel shows that event's description and when it happened

  Scenario: Pull Requests that touched the entity are shown as navigable evidence
    Given a pull request 217 added retry handling to "PaymentProcessor"
    When the developer opens Context Rewind for "PaymentProcessor"
    Then Context Rewind references Pull Request 217
    And that reference can be opened directly

  Scenario: An AI-generated narrative is shown clearly labeled as an interpretation
    Given "PaymentProcessor" has enough history for Athena to generate a narrative about it
    When the developer opens Context Rewind for "PaymentProcessor"
    Then Context Rewind shows the AI-generated narrative
    And it is visually labeled as an AI-generated interpretation, not a project fact

  Scenario: Insufficient history is stated plainly instead of an empty timeline
    Given "UnknownWidget" has no recorded history
    When the developer opens Context Rewind for "UnknownWidget"
    Then Context Rewind states that not enough historical information is available for "UnknownWidget"
    And it does not render an empty timeline

  Scenario: Context Rewind cannot be opened when no pull request is currently selected
    Given no pull request is currently selected
    When the developer tries to open Context Rewind for "PaymentProcessor"
    Then Athena tells the developer to select a pull request first
