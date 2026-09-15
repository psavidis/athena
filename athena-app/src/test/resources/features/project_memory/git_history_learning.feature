Feature: Git History Learning: co-change patterns as project memory
  Git history is the first evidence source Athena's project memory
  (ticket 121) learns from (ticket 170). Two files that recur together
  across multiple commits are a signal worth remembering — a fact about
  the project, not a rule enforced on anyone — while two files that
  merely happened to change together once are noise, not a pattern.
  Every learned co-change fact is recorded through the storage
  introduced in ticket 169, with the commits behind it named explicitly
  as provenance, and reported as Athena's own inference rather than
  something a developer confirmed.

  Scenario: Two files that repeatedly change together become a learned co-change fact
    Given a project whose git history has "OrderService.java" and "OrderProjection.java" modified together in 5 commits
    When Athena learns from that project's git history
    Then querying that project's memory returns a fact that "OrderService.java" and "OrderProjection.java" change together
    And that fact's evidence names 5 commits
    And that fact is reported as an inferred pattern, not a developer-confirmed fact

  Scenario: Files that changed together only once are not learned as a pattern
    Given a project whose git history has "ReadmeTypo.java" and "ChangeLog.java" modified together in 1 commit
    When Athena learns from that project's git history
    Then querying that project's memory does not return a fact about "ReadmeTypo.java" and "ChangeLog.java"

  Scenario: A project with no git history yet has no learned memory
    Given a project whose git history contains no commits
    When Athena learns from that project's git history
    Then Athena's project memory for that project contains no learned facts
    And learning does not fail with an exception

  Scenario: Multiple independent co-change patterns are each recorded
    Given a project whose git history has "Order.java" and "OrderProjection.java" modified together in 4 commits
    And that project's git history separately has "Invoice.java" and "InvoiceView.java" modified together in 3 commits
    When Athena learns from that project's git history
    Then querying that project's memory returns a fact that "Order.java" and "OrderProjection.java" change together
    And querying that project's memory returns a fact that "Invoice.java" and "InvoiceView.java" change together

  Scenario: Learning from the same git history again does not duplicate an already-learned pattern
    Given a project whose git history has "OrderService.java" and "OrderProjection.java" modified together in 5 commits
    And Athena has already learned from that project's git history once
    When Athena learns from that project's git history again
    Then querying that project's memory returns exactly one fact that "OrderService.java" and "OrderProjection.java" change together
