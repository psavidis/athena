Feature: Review Replay semantic timeline
  A developer replaying a recorded review session moves through its
  meaningful moments chronologically or jumps directly to one, instead of
  reading a raw event log (ticket #211). Moments that belong together
  (e.g. a question and the decision it led to) are shown grouped. No
  semantic canvas integration yet (next ticket).

  Scenario: A developer sees the Replay's moments in chronological order
    Given a Replay with a confirmed question, then a confirmed decision
    When the developer opens the Replay timeline
    Then the timeline shows the question before the decision
    And each moment is labeled with its kind

  Scenario: Moments referencing the same entity are shown grouped
    Given a Replay with a confirmed question about "OrderService", then a confirmed decision about "OrderService"
    When the developer opens the Replay timeline
    Then the question and the decision are shown in the same group

  Scenario: Moments referencing different entities are not grouped together
    Given a Replay with a confirmed question about "OrderService", then a confirmed decision about "PaymentService"
    When the developer opens the Replay timeline
    Then the question and the decision are shown in different groups

  Scenario: A developer steps to the next moment
    Given a Replay with a confirmed question, then a confirmed decision
    And the developer is viewing the first moment
    When the developer steps to the next moment
    Then the developer is viewing the decision

  Scenario: A developer steps to the previous moment
    Given a Replay with a confirmed question, then a confirmed decision
    And the developer is viewing the last moment
    When the developer steps to the previous moment
    Then the developer is viewing the question

  Scenario: Stepping past the last moment has no effect
    Given a Replay with a confirmed question, then a confirmed decision
    And the developer is viewing the last moment
    When the developer steps to the next moment
    Then the developer is still viewing the decision

  Scenario: Stepping before the first moment has no effect
    Given a Replay with a confirmed question, then a confirmed decision
    And the developer is viewing the first moment
    When the developer steps to the previous moment
    Then the developer is still viewing the question

  Scenario: A developer jumps directly to a moment
    Given a Replay with a confirmed question, then a confirmed decision
    And the developer is viewing the first moment
    When the developer jumps to the decision
    Then the developer is viewing the decision

  Scenario: A Replay with no moments shows an empty timeline
    Given a Replay with no moments
    When the developer opens the Replay timeline
    Then the timeline is shown empty
