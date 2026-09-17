Feature: Review Briefing historical and Knowledge Base context
  A developer's briefing surfaces relevant history or project knowledge
  for a PR's focus-area entities, only when it materially helps explain
  the change — a pointer, not a dump (ticket #222). Reuses Context
  Rewind's existing aggregation (ticket #161) rather than gathering new
  data.

  Scenario: A focus-area entity with prior PR history gets historical context
    Given a focus-area entity "PaymentProcessor" touched by a prior Pull Request
    When Athena generates the Review Briefing's historical and Knowledge Base context
    Then the generated historical context is present for "PaymentProcessor"

  Scenario: A focus-area entity with a Knowledge Base entry gets relevant knowledge
    Given a focus-area entity "PaymentProcessor" with a Knowledge Base entry
    When Athena generates the Review Briefing's historical and Knowledge Base context
    Then the generated relevant knowledge is present for "PaymentProcessor"

  Scenario: A focus-area entity with no history or knowledge contributes nothing
    Given a focus-area entity "PaymentProcessor" with no history or knowledge
    When Athena generates the Review Briefing's historical and Knowledge Base context
    Then the generated historical context is empty
    And the generated relevant knowledge is empty

  Scenario: A PR with no focus areas has no historical or Knowledge Base context
    Given no focus-area entities
    When Athena generates the Review Briefing's historical and Knowledge Base context
    Then the generated historical context is empty
    And the generated relevant knowledge is empty
