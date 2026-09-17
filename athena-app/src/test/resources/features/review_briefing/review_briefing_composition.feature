Feature: Review Briefing composition
  Athena composes a PR's full Review Briefing from the four generators
  its constituent tickets built (change summary, focus areas,
  uncertainty/questions, historical and Knowledge Base context) rather
  than serving each independently (ticket #223's own backend
  prerequisite). Focus areas drive which entities' historical context is
  looked up.

  Scenario: A developer requests the Review Briefing for the selected PR
    Given a PR with a detected Change and generators that produce real briefing content
    When a developer requests the Review Briefing
    Then the Review Briefing's change summary is present
    And the Review Briefing has at least 1 focus area

  Scenario: Requesting a Review Briefing with no PR selected is rejected
    Given no PR is selected
    When a developer requests the Review Briefing
    Then the Review Briefing request is rejected as invalid
