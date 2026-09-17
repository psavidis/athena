Feature: Review Replay — promote a learning or decision to project memory
  A developer explicitly promotes a learned or decided item from a
  Replay's outcome (ticket #215) into Athena's durable project memory
  (ticket #169), preserving where it came from. Nothing is ever
  auto-promoted — this is always a developer's own explicit action.

  Scenario: A developer promotes a learned item to project memory
    Given a developer has PR 42 in "acme/widgets" selected
    And "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has tagged the current moment as an insight
    And "Petros" has confirmed that moment
    And "Petros" has stopped the recording
    When a developer promotes that learned item to project memory as "Retries are capped at 3 attempts"
    Then project memory has an entry "Retries are capped at 3 attempts"
    And that entry is developer-confirmed
    And that entry's evidence mentions "acme/widgets", PR 42, and "OrderService"

  Scenario: A developer promotes a decided item to project memory
    Given a developer has PR 42 in "acme/widgets" selected
    And "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "PaymentGateway" entity
    And "Petros" has tagged the current moment as a decision
    And "Petros" has confirmed that moment
    And "Petros" has stopped the recording
    When a developer promotes that decided item to project memory as "Use idempotency keys for retries"
    Then project memory has an entry "Use idempotency keys for retries"

  Scenario: Promoting with no Pull Request selected is rejected
    Given a developer has no PR or Diff selected
    When a developer attempts to promote a learning to project memory as "Some fact"
    Then the promotion request is rejected as invalid

  Scenario: Promoting the same fact twice does not duplicate it
    Given a developer has PR 42 in "acme/widgets" selected
    And "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has tagged the current moment as an insight
    And "Petros" has confirmed that moment
    And "Petros" has stopped the recording
    When a developer promotes that learned item to project memory as "Retries are capped at 3 attempts"
    And a developer promotes that learned item to project memory as "Retries are capped at 3 attempts"
    Then project memory has exactly 1 entry "Retries are capped at 3 attempts"
