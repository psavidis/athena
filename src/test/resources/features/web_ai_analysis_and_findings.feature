Feature: Web AI analysis trigger & findings review
  A reviewer inspects the context boundary, triggers AI analysis, and
  independently accepts or dismisses each surfaced finding, all via the API
  (ticket #77).

  Scenario: Reviewer inspects the context boundary before triggering analysis
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    And the reviewer has set the rename Change's review state to "REVIEWED"
    When the reviewer requests the AI context boundary via the API
    Then the boundary response lists the rename Change among the included Changes
    And the boundary response confirms private notes are excluded

  Scenario: Requesting the context boundary without connecting to GitHub fails
    Given the reviewer has not connected to GitHub
    When the reviewer requests the AI context boundary via the API
    Then the request is rejected as unauthorized

  Scenario: Requesting the context boundary before selecting a PR fails
    Given the reviewer is connected to GitHub
    But the reviewer has not selected a PR
    When the reviewer requests the AI context boundary via the API
    Then the request is rejected because no PR is selected

  Scenario: Reviewer triggers AI analysis and sees the findings
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    And the reviewer has set the rename Change's review state to "REVIEWED"
    And the configured AI provider will return a finding "You may have missed the null check" related to the rename Change
    When the reviewer triggers AI analysis via the API
    Then the findings response includes "You may have missed the null check"
    And that finding is pending
    And that finding's jump target is the rename Change

  Scenario: A general finding not tied to any Change has no jump target
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    And the reviewer has set the rename Change's review state to "REVIEWED"
    And the configured AI provider will return a general finding "Consider adding integration tests"
    When the reviewer triggers AI analysis via the API
    Then the findings response includes "Consider adding integration tests"
    And that finding has no jump target

  Scenario: Triggering AI analysis without connecting to GitHub fails
    Given the reviewer has not connected to GitHub
    When the reviewer triggers AI analysis via the API
    Then the request is rejected as unauthorized

  Scenario: Triggering AI analysis before selecting a PR fails
    Given the reviewer is connected to GitHub
    But the reviewer has not selected a PR
    When the reviewer triggers AI analysis via the API
    Then the request is rejected because no PR is selected

  Scenario: Reviewer accepts a finding independently of the others
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    And the reviewer has set the rename Change's review state to "REVIEWED"
    And the configured AI provider will return a finding "First finding" related to the rename Change
    And the configured AI provider will return a general finding "Second finding"
    And the reviewer has triggered AI analysis
    When the reviewer accepts the finding "First finding"
    Then the finding "First finding" is accepted
    And the finding "Second finding" is still pending

  Scenario: Reviewer dismisses a finding independently of the others
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    And the reviewer has set the rename Change's review state to "REVIEWED"
    And the configured AI provider will return a finding "First finding" related to the rename Change
    And the configured AI provider will return a general finding "Second finding"
    And the reviewer has triggered AI analysis
    When the reviewer dismisses the finding "First finding"
    Then the finding "First finding" is dismissed
    And the finding "Second finding" is still pending

  Scenario: Evaluating a finding that doesn't exist fails
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    And the reviewer has triggered AI analysis
    When the reviewer accepts a finding id that was never returned
    Then the request is rejected because the finding was not found

  Scenario: Evaluating a finding before any analysis has been triggered fails
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    When the reviewer accepts a finding id that was never returned
    Then the request is rejected because no analysis has been triggered yet
