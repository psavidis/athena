Feature: Conversation reconstruction from a transcript
  Athena reconstructs the conversation around a semantic entity from
  aligned transcript segments (ticket #214), so a developer can read
  what was discussed about that entity without reading the whole
  transcript. Segments are grouped by the entity reference #209's
  alignment already determined, in the order they were spoken — this is
  a verbatim regrouping, not an AI-generated summary: no
  question-to-answer inference or decision-rationale summarization is
  attempted here, since that would need real NLP/AI infrastructure this
  ticket does not have (Athena's existing AiProvider seam is scoped to
  post-review finding analysis, not general summarization — reusing it
  here would be a scope decision this ticket doesn't own). Reconstructed
  content must always be clearly distinguishable from an unaligned
  segment and from an explicit human Moment (ticket #205/#206).

  Scenario: Segments about the same entity are reconstructed into one conversation
    Given aligned segments:
      | text                          | speaker | entity              |
      | Could this execute twice?     | Alice   | PaymentProcessor    |
      | Only if the network retries.  | Bob     | PaymentProcessor    |
    When Athena reconstructs the conversation for "PaymentProcessor"
    Then the reconstructed conversation has 2 segments, in speaking order
    And every segment is labeled as verbatim transcript

  Scenario: Segments about different entities form separate conversations
    Given aligned segments:
      | text                          | speaker | entity              |
      | Could this execute twice?     | Alice   | PaymentProcessor    |
      | Should this retry at all?     | Bob     | RetryWorker         |
    When Athena reconstructs the conversation for "PaymentProcessor"
    Then the reconstructed conversation has 1 segment, in speaking order

  Scenario: Unaligned segments are excluded from every reconstructed conversation
    Given aligned segments:
      | text                          | speaker | entity              |
      | Let's get started             | Bob     |                     |
      | Could this execute twice?     | Alice   | PaymentProcessor    |
    When Athena reconstructs the conversation for "PaymentProcessor"
    Then the reconstructed conversation has 1 segment, in speaking order

  Scenario: No transcript is available to reconstruct from (today's real-world case)
    Given no aligned segments are available
    When Athena reconstructs the conversation for "PaymentProcessor"
    Then the reconstructed conversation is empty

  Scenario: Reconstructing for an entity with no discussion yields an empty conversation
    Given aligned segments:
      | text                          | speaker | entity              |
      | Could this execute twice?     | Alice   | PaymentProcessor    |
    When Athena reconstructs the conversation for "RetryWorker"
    Then the reconstructed conversation is empty
