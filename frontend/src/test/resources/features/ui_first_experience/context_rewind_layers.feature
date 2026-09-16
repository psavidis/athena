Feature: Context Rewind — Context Layers
  Context Rewind (ticket #190) separates different kinds of context
  visually — Current, Evolution, Discussions, Decisions, Knowledge —
  instead of mixing them into one AI-generated narrative. These behave as
  progressively explorable layers of the same reconstructed context, not
  separate tabs: more than one can be expanded at once.

  Athena has no structured source for "what developers discussed" or
  "what the team decided" today (only aggregate Pull-Request-touched
  facts, not comment threads or decision records) and no current-state
  analysis capability either — so Current, Discussions, and Decisions
  always report that nothing is recorded for them, same as any other
  layer would when it genuinely has nothing. This is a disclosed,
  deliberate limitation, not a bug.

  Scenario: Context Rewind organizes its context into five distinct layers
    Given the developer has Context Rewind open for "PaymentProcessor" at the Overview level
    Then Context Rewind lists the "Current", "Evolution", "Discussions", "Decisions", and "Knowledge" layers

  Scenario: Expanding the Knowledge layer reveals what Athena remembers
    Given "PaymentProcessor" has a Knowledge Base fact "Owned by the payments team"
    When the developer expands the "Knowledge" layer
    Then it shows "Owned by the payments team"

  Scenario: More than one layer can be open at the same time
    Given the developer has expanded the "Knowledge" layer
    When the developer also expands the "Evolution" layer
    Then both the "Knowledge" and "Evolution" layers remain expanded

  Scenario: A layer with no available content says so plainly instead of looking broken
    When the developer expands the "Discussions" layer
    Then it states that nothing is recorded for that layer
