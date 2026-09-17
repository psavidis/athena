Feature: Review Briefing focus areas
  Athena tells a developer which 2-4 parts of a PR's Changes deserve
  their attention, ranked by a conservative, deterministic heuristic over
  existing semantic classification (ticket #220) — not a generic
  severity score, and not a new classification capability.

  Scenario: A PR with several Changes gets ranked focus areas
    Given a PR with 5 detected Changes of varying semantic significance
    When Athena generates the Review Briefing's focus areas
    Then the briefing has at most 4 focus areas
    And the focus areas are ordered from most to least significant

  Scenario: A Change classified under Responsibility or Architecture ranks higher
    Given a PR with a Change classified under "RESPONSIBILITY" and an otherwise-equivalent Change with no such classification
    When Athena generates the Review Briefing's focus areas
    Then the Responsibility-classified Change's focus area ranks first

  Scenario: A PR with fewer than 4 Changes returns however many exist
    Given a PR with 2 detected Changes
    When Athena generates the Review Briefing's focus areas
    Then the briefing has 2 focus areas

  Scenario: A PR with no detected Changes has no focus areas
    Given a PR with no detected Changes for the focus areas
    When Athena generates the Review Briefing's focus areas
    Then the briefing has 0 focus areas

  Scenario: Each focus area references the entity its Change concerns
    Given a PR with 2 detected Changes
    When Athena generates the Review Briefing's focus areas
    Then every focus area references a semantic entity
