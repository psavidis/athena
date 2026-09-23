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
    Then the generated focus areas number 2

  Scenario: A PR with no detected Changes has no focus areas
    Given a PR with no detected Changes for the focus areas
    When Athena generates the Review Briefing's focus areas
    Then the generated focus areas number 0

  Scenario: Each focus area references the entity its Change concerns
    Given a PR with 2 detected Changes
    When Athena generates the Review Briefing's focus areas
    Then every focus area references a semantic entity

  # Ticket #286: focus areas point at the production changes that matter most.

  Scenario: Production changes are chosen over test changes
    Given a PR with 4 added production methods and 3 added test methods
    When Athena generates the Review Briefing's focus areas
    Then no focus area is a test change

  Scenario: Test changes fill the focus areas only when there are few production changes
    Given a PR with 1 added production method and 3 added test methods
    When Athena generates the Review Briefing's focus areas
    Then the first focus area is the production change
    And the generated focus areas number 4

  Scenario: A behavioral change ranks above everything else
    Given a PR where one method's control flow changed and several classes were added
    When Athena generates the Review Briefing's focus areas
    Then the first focus area is the control-flow change

  Scenario: A relocation ranks above additions
    Given a PR where a method moved to another class and several classes were added
    When Athena generates the Review Briefing's focus areas
    Then the move is among the focus areas

  Scenario: A public API change ranks above additions
    Given a PR where a method's signature changed and several classes were added
    When Athena generates the Review Briefing's focus areas
    Then the signature change is among the focus areas

  Scenario: Equally ranked changes keep the order they were detected in, not alphabetical order
    Given a PR where classes "Zulu", "Mike" and "Alpha" were added, in that detection order
    When Athena generates the Review Briefing's focus areas
    Then the focus areas follow the detection order of those classes
