Feature: Context Rewind — "Explain Why" quick actions
  Context Rewind (ticket #193) offers a fixed set of lightweight
  contextual actions at the Orientation level — "Why does this exist?",
  "Why was this introduced?", "Why was this changed?", "Why is it
  implemented this way?", "What happened here?", "What did reviewers
  question?", "What was decided?", "What changed since I last saw this?"
  — instead of requiring the developer to type a free-text prompt. Each
  answer is evidence-first: paired with the specific facts it's based on,
  or a plain statement that nothing is available, never a fabricated
  answer.

  Scenario: A "Why" question shows the AI-generated narrative, evidence-cited
    Given "PaymentProcessor" has an AI-generated narrative and 2 recorded events and 1 Pull Request
    When the developer asks "Why does this exist?"
    Then the answer shows the AI-generated narrative
    And it cites "Based on 2 recorded events and 1 Pull Request"

  Scenario: A "Why" question with no narrative available says so plainly
    Given "PaymentProcessor" has no AI-generated narrative
    When the developer asks "Why was this changed?"
    Then the answer states that not enough information is available to answer that

  Scenario: "What did reviewers question?" points to the entity's Pull Request review
    Given "PaymentProcessor" is referenced by Pull Request 217
    When the developer asks "What did reviewers question?"
    Then the answer points to the review for Pull Request 217

  Scenario: "What did reviewers question?" says so when no Pull Request is recorded
    Given "PaymentProcessor" has no Pull Request recorded
    When the developer asks "What did reviewers question?"
    Then the answer states that no Pull Request is recorded

  Scenario: "What was decided?" states plainly that nothing is recorded
    When the developer asks "What was decided?"
    Then the answer states that nothing is recorded for decisions
