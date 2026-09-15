Feature: Incremental Project Memory Updates
  Learning from git history (ticket 170) or Pull Requests (ticket 171)
  would otherwise start over from the very beginning of the evidence on
  every single pass. This feature gives both learning sources a shared,
  general mechanism — a recorded high-water mark of what evidence has
  already been incorporated into memory — so a later pass only
  processes evidence that showed up since the last one, while still
  arriving at the exact same facts a single, full pass over all the
  evidence would have.

  Scenario: A second git-history learning pass builds on the first pass's progress
    Given a project whose git history has "OrderService.java" and "OrderProjection.java" modified together in 1 commit
    And Athena has already learned from that project's git history once
    And that project's git history separately gets one more commit modifying "OrderService.java" and "OrderProjection.java" together
    When Athena learns from that project's git history again
    Then querying that project's memory returns a fact that "OrderService.java" and "OrderProjection.java" change together
    And that fact's evidence names 2 commits

  Scenario: A second Pull Request learning pass builds on the first pass's progress
    Given a project whose merged Pull Requests include "OrderService.java" and "OrderProjection.java" touched together in 1 Pull Request
    And Athena has already learned from that project's Pull Requests once
    And that project's merged Pull Requests separately gain one more Pull Request touching "OrderService.java" and "OrderProjection.java" together
    When Athena learns from that project's Pull Requests again
    Then querying that project's memory returns a fact that "OrderService.java" and "OrderProjection.java" were touched together across merged Pull Requests
    And that fact's evidence names 2 Pull Requests
