Feature: Project Memory Foundation
  Athena's project memory (ticket #121) is a persistent, project-local
  record of what Athena has learned about a project — distinct from any
  single review session, and distinct from Athena's per-installation
  config or its cache. Every learning source ticket #121 introduces (git
  history, Pull Requests, external knowledge, ...) builds on this same
  storage boundary (ticket #169), so its behavior must be correct before
  anything is built on top of it: a recorded fact roundtrips intact with
  its provenance and confidence preserved, a project with no memory yet
  behaves as a normal, error-free starting point, and that same
  error-free starting point is exactly what a project returns to if its
  memory is ever deleted.

  Scenario: A project that has not learned anything yet has empty memory
    Given a project with no recorded memory
    When Athena's project memory is queried for that project
    Then the result is empty
    And no exception is thrown

  Scenario: A learned fact is recorded with its provenance and can be read back
    Given a project with no recorded memory
    When Athena records the learned fact "Order and OrderProjection frequently change together" for that project, with evidence "18 git commits" and confidence "high"
    Then querying that project's memory returns a fact "Order and OrderProjection frequently change together"
    And that fact's evidence is "18 git commits"
    And that fact's confidence is "high"

  Scenario: Recording a new fact preserves facts already recorded
    Given a project whose memory already contains the fact "Order and OrderProjection frequently change together"
    When Athena records the additional fact "Reporting adapters intentionally bypass the repository boundary" for that project
    Then querying that project's memory returns both facts

  Scenario: An inferred pattern is distinguished from a developer-confirmed fact
    Given a project with no recorded memory
    When Athena records the inferred pattern "these components appear to change together" for that project, without developer confirmation
    And Athena records the fact "reporting adapters intentionally bypass the repository boundary" for that project, as confirmed by a developer
    Then querying that project's memory reports the first as an inferred pattern
    And querying that project's memory reports the second as a developer-confirmed fact

  Scenario: One project's memory is kept separate from another's
    Given two different projects, each with recorded memory
    When Athena records a fact for the first project
    Then querying the second project's memory does not return that fact

  Scenario: A project's memory is stored at a predictable location under the project itself
    Given a project with recorded memory
    Then a ".athena/memory" directory exists under that project's own root, containing the recorded memory

  Scenario: Deleting a project's memory directory returns it to a fresh, empty state
    Given a project whose ".athena/memory" directory contains recorded memory
    When that directory is deleted directly from disk
    Then querying that project's memory is empty
    And no exception is thrown
