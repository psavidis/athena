Feature: Pull Request History Learning: closed and open PRs as project memory
  Pull Requests are a richer evidence source than git history (ticket
  170) alone: they carry not just which files changed together, but
  whether that change was ultimately accepted or not, and whether it is
  still an open question. Two files repeatedly touched together across
  merged Pull Requests are a signal worth remembering, exactly as with
  git-commit co-change, with the Pull Request numbers behind it named as
  provenance. A Pull Request that was closed without merging is
  recorded as its own kind of evidence — a change that was proposed but
  not accepted — never conflated with an accepted pattern. And an open
  Pull Request, still under discussion, is never recorded with the same
  confidence as established, merged history: it is current context, not
  yet a fact about the project.

  Scenario: Files repeatedly touched together across merged Pull Requests become a learned fact
    Given a project whose merged Pull Requests include "OrderService.java" and "OrderProjection.java" touched together in 3 Pull Requests
    When Athena learns from that project's Pull Requests
    Then querying that project's memory returns a fact that "OrderService.java" and "OrderProjection.java" were touched together across merged Pull Requests
    And that fact's evidence names 3 Pull Requests
    And that fact is reported as an inferred pattern from Pull Request history, not a developer-confirmed fact

  Scenario: Files touched together in only one merged Pull Request are not learned as a pattern
    Given a project whose merged Pull Requests include "ReadmeTypo.java" and "ChangeLog.java" touched together in 1 Pull Request
    When Athena learns from that project's Pull Requests
    Then querying that project's memory does not return a fact that "ReadmeTypo.java" and "ChangeLog.java" were touched together across merged Pull Requests

  Scenario: A Pull Request closed without merging is recorded as a rejected change, not an accepted pattern
    Given a project whose Pull Request 42 touched "LegacyExport.java" and "LegacyImport.java" and was closed without merging
    When Athena learns from that project's Pull Requests
    Then querying that project's memory returns a fact that "LegacyExport.java" and "LegacyImport.java" were proposed together in Pull Request 42, which was closed without merging
    And querying that project's memory does not return a fact that "LegacyExport.java" and "LegacyImport.java" were touched together across merged Pull Requests

  Scenario: Files touched together in a still-open Pull Request are recorded with provisional confidence
    Given a project whose open Pull Request 55 touches "Checkout.java" and "Payment.java"
    When Athena learns from that project's Pull Requests
    Then querying that project's memory returns a fact that "Checkout.java" and "Payment.java" were touched together in an open Pull Request
    And that fact's confidence is reported as "provisional"

  Scenario: A project with no closed Pull Requests yet has no learned memory
    Given a project with no closed Pull Requests
    When Athena learns from that project's Pull Requests
    Then Athena's Pull Request learning leaves that project's memory empty
    And Pull Request learning does not fail with an exception

  Scenario: Learning from the same Pull Requests again does not duplicate an already-learned pattern
    Given a project whose merged Pull Requests include "OrderService.java" and "OrderProjection.java" touched together in 3 Pull Requests
    And Athena has already learned from that project's Pull Requests once
    When Athena learns from that project's Pull Requests again
    Then querying that project's memory returns exactly one fact that "OrderService.java" and "OrderProjection.java" were touched together across merged Pull Requests
