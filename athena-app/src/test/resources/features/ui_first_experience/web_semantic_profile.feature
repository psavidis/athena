Feature: Semantic Profile API
  The Athena frontend needs a Change's full Semantic Profile — every
  dimension it's classified along, whether each classification is
  Observed (deterministic) or Inferred (heuristic), a confidence value,
  and the evidence supporting it — so the Semantic Change Explorer
  (issue #91) has real data to render instead of the flat Change Map
  response alone.

  Scenario: Reviewer requests the Semantic Profile for a Change with a deterministic classification
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    When the reviewer requests the Semantic Profile for the rename Change
    Then the Semantic Profile includes a "Structural" entry for the "Rename" concept
    And that entry is marked Observed with 100% confidence
    And that entry includes supporting evidence

  Scenario: Reviewer requests the Semantic Profile for a Change with a heuristic classification
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose head revision adds a class named "UserController"
    When the reviewer requests the Semantic Profile for the newly-added Change
    Then the Semantic Profile includes an "Architecture" entry for the "Driving Adapter" concept
    And that entry is marked Inferred with a confidence below 100%
    And that entry includes supporting evidence

  Scenario: A Pattern entry names the structural changes that support it
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose head revision adds a class named "UserBuilder"
    When the reviewer requests the Semantic Profile for the newly-added Change
    Then the Semantic Profile includes a "Pattern" entry for the "Builder" concept
    And that entry lists "Add" as a supporting structural change

  Scenario: A dimension the Change has no classification for is absent from its Semantic Profile
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    When the reviewer requests the Semantic Profile for the rename Change
    Then the Semantic Profile has no "Pattern" entry

  Scenario: Requesting a Semantic Profile without connecting to GitHub fails
    Given the reviewer has not connected to GitHub
    When the reviewer requests the Semantic Profile for a Change
    Then the request is rejected as unauthorized

  Scenario: Requesting a Semantic Profile before selecting a PR fails
    Given the reviewer is connected to GitHub
    But the reviewer has not selected a PR
    When the reviewer requests the Semantic Profile for a Change
    Then the request is rejected because no PR is selected

  Scenario: Requesting a Semantic Profile for an unknown Change fails
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    When the reviewer requests the Semantic Profile for an unknown Change
    Then the request is rejected because the Change was not found
