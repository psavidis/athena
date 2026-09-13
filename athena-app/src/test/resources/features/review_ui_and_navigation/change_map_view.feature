Feature: Change Map as default navigation entry point
  The reviewer's primary navigation structure is Changes, not files — a
  Change Map view presents the PR's Changes as the default entry point,
  with each entry showing enough to identify it without opening it
  (epic #5 §14).

  Scenario: The Change Map lists every Change with its category, description, and review state
    Given a PR with a rename Change and a mechanical replacement Change
    When the reviewer opens the Change Map
    Then the Change Map lists 2 entries
    And an entry shows its category
    And an entry shows its transformation description
    And an entry shows its current review state

  Scenario: A newly-produced Change appears in the Change Map as Unseen
    Given a PR with a newly-produced rename Change
    When the reviewer opens the Change Map
    Then the entry for that Change shows review state "Unseen"

  Scenario: The Change Map reflects a Change's review state after it changes
    Given a PR with a rename Change
    And the reviewer has marked that Change as "Reviewed"
    When the reviewer opens the Change Map
    Then the entry for that Change shows review state "Reviewed"

  Scenario: An empty PR produces an empty Change Map, not an error
    Given a PR with no detected Changes
    When the reviewer opens the Change Map
    Then the Change Map lists 0 entries

  Scenario: Multiple Changes on the same class are grouped into one class group
    Given a PR where class "DeviceConfiguration" has two methods with changed signatures, and an unrelated rename elsewhere
    When the reviewer opens the Change Map
    Then the Change Map has a class group for "DeviceConfiguration" containing 2 entries
    And the Change Map has a class group for "Greeter" containing 1 entry
