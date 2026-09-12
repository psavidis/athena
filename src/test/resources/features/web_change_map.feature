Feature: Web Change Map & PR Understanding View
  Once a reviewer has selected a PR, they can request its Change Map and PR
  Understanding summary so they understand the PR's Changes without reading
  a raw diff (ticket #74).

  Scenario: Reviewer views the Change Map and PR Understanding summary for a selected PR
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    When the reviewer requests the Change Map
    Then the Change Map response includes the PR's title
    And the Change Map lists the rename Change under category "STRUCTURAL"
    And the Change Map's category counts show 1 Change under "STRUCTURAL"

  Scenario: Requesting the Change Map without connecting to GitHub fails
    Given the reviewer has not connected to GitHub
    When the reviewer requests the Change Map
    Then the request is rejected as unauthorized

  Scenario: Requesting the Change Map before selecting a PR fails
    Given the reviewer is connected to GitHub
    But the reviewer has not selected a PR
    When the reviewer requests the Change Map
    Then the request is rejected because no PR is selected
