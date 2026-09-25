Feature: Checking out a revision survives an unreliable git host
  A connection that stalls or drops must not hang or needlessly fail an
  analysis: a stalled fetch is aborted with a clear error, a transient network
  failure is retried once, and a revision that doesn't exist fails at once
  (ticket #359; third expansion baseline, K7).

  Scenario: A fetch that stalls is aborted within the time limit
    Given a git host whose fetch never finishes
    When Athena checks out a revision with a fetch time limit of 2 seconds
    Then the checkout fails within 15 seconds with an error saying the fetch timed out

  Scenario: A fetch that fails once on a network error is retried
    Given a git host that resets the connection on the first fetch
    When Athena checks out a revision
    Then the checkout succeeds
    And the host saw 2 fetches

  Scenario: A revision that does not exist is not retried
    Given a git host without the requested revision
    When Athena checks out that revision
    Then the checkout fails
    And the host saw 1 fetch

  Scenario: The fetch asks git to abort a transfer that stays too slow
    Given a git host that answers normally
    When Athena checks out a revision
    Then the checkout succeeds
    And the fetch asked git to abort below 1000 bytes per second for 60 seconds
