Feature: Review Briefing domain model
  A Review Briefing is structured data — a change summary, focus areas,
  uncertainties, questions, historical context, relevant knowledge, and a
  recommended starting point — not a single generated paragraph, so each
  part can link back to the Semantic Canvas independently (ticket #218).
  No generation logic yet: this defines the shape only.

  Scenario: A briefing groups its content into named sections
    Given a Review Briefing with a change summary, one focus area, one uncertainty, one question, one piece of historical context, one piece of relevant knowledge, and a recommended starting point
    Then the briefing's change summary is present
    And the briefing has 1 focus area
    And the briefing has 1 uncertainty
    And the briefing has 1 question
    And the briefing has 1 piece of historical context
    And the briefing has 1 piece of relevant knowledge
    And the briefing's recommended starting point is present

  Scenario: A briefing item references the semantic entity it concerns
    Given a Review Briefing with a focus area about the "OrderService" entity
    Then that focus area references "OrderService"

  Scenario: A briefing item can omit its entity reference
    Given a Review Briefing with a focus area with no entity reference
    Then that focus area has no entity reference

  Scenario: An empty section is reported as present but empty, not absent
    Given a Review Briefing with no focus areas
    Then the briefing has 0 focus areas
    And the briefing's focus areas section is present

  Scenario: Two briefings are independent values
    Given a Review Briefing with 1 focus area
    And a second, separate Review Briefing with 2 focus areas
    Then the first briefing still has 1 focus area
    And the second briefing has 2 focus areas
