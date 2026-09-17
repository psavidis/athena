Feature: Review Briefing uncertainty and suggested questions
  Athena tells a developer plainly when it isn't sure why something
  changed, and suggests investigation questions, rather than presenting
  a guess as fact (ticket #221). Reuses the same AI-provider seam
  Context Rewind's narrative and the semantic change summary (#219)
  already established — not a new deterministic algorithm.

  Scenario: A PR with focus areas gets uncertainties and questions
    Given a PR with a detected Change and an AI reasoning pass that flags it as uncertain
    When Athena generates the Review Briefing's uncertainty and questions
    Then the generated uncertainties are present
    And the generated questions are present

  Scenario: A PR with no focus areas has no uncertainties or questions
    Given a PR with no detected Changes for uncertainty and questions
    When Athena generates the Review Briefing's uncertainty and questions
    Then the generated uncertainty count is 0
    And the generated question count is 0

  Scenario: A confidently-explainable PR may still have zero uncertainties
    Given a PR with a detected Change and an AI reasoning pass that finds nothing uncertain
    When Athena generates the Review Briefing's uncertainty and questions
    Then the generated uncertainty count is 0

  Scenario: Uncertainties and questions reference their semantic entity
    Given a PR with a detected Change and an AI reasoning pass that flags it as uncertain
    When Athena generates the Review Briefing's uncertainty and questions
    Then every generated uncertainty references a semantic entity
