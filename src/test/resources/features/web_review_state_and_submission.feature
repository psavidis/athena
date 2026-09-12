Feature: Web review state, pre-submission summary & GitHub submission
  A reviewer sets each Change's review state, sees a pre-submission summary
  before submitting, and — after explicit confirmation — has their comments
  and review decision synced to the real GitHub Pull Request (ticket #76).

  Scenario: Reviewer sets a Change's review state
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    When the reviewer sets the rename Change's review state to "REVIEWED"
    Then the Change Map shows the rename Change's review state as "REVIEWED"

  Scenario: Setting review state for a Change that no longer matches any detected Change fails
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    When the reviewer sets the review state of a Change key that does not match any current Change
    Then the request is rejected because the Change was not found

  Scenario: Pre-submission summary reflects reviewed, mechanical and concern Changes
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    And the reviewer has set the rename Change's review state to "REVIEWED"
    And the reviewer has posted the comment "Looks fine" scoped to the rename Change
    When the reviewer requests the pre-submission summary
    Then the summary lists the rename Change among the reviewed Changes
    And the summary shows a comment count of 1
    And the summary previews the GitHub action "APPROVE"

  Scenario: An open concern previews a request-changes submission
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    And the reviewer has set the rename Change's review state to "CONCERN"
    When the reviewer requests the pre-submission summary
    Then the summary lists the rename Change among the concern Changes
    And the summary previews the GitHub action "REQUEST_CHANGES"

  Scenario: Requesting the pre-submission summary without connecting to GitHub fails
    Given the reviewer has not connected to GitHub
    When the reviewer requests the pre-submission summary
    Then the request is rejected as unauthorized

  Scenario: Requesting the pre-submission summary before selecting a PR fails
    Given the reviewer is connected to GitHub
    But the reviewer has not selected a PR
    When the reviewer requests the pre-submission summary
    Then the request is rejected because no PR is selected

  Scenario: Confirmed submission syncs comments and the review decision to GitHub
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And GitHub sync is enabled for that Pull Request
    And the reviewer has requested the Change Map
    And the reviewer has set the rename Change's review state to "REVIEWED"
    And the reviewer has posted the comment "Nice cleanup" scoped to the rename Change
    When the reviewer confirms and submits the review
    Then GitHub shows the posted comment "Nice cleanup"
    And GitHub shows a posted "APPROVE" review
    And the submission response reports success

  Scenario: Confirmed submission with an open concern requests changes on GitHub
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And GitHub sync is enabled for that Pull Request
    And the reviewer has requested the Change Map
    And the reviewer has set the rename Change's review state to "CONCERN"
    When the reviewer confirms and submits the review
    Then GitHub shows a posted "REQUEST_CHANGES" review

  Scenario: Submitting without confirmation never reaches GitHub
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And GitHub sync is enabled for that Pull Request
    And the reviewer has requested the Change Map
    And the reviewer has posted the comment "Draft thought" scoped to the rename Change
    When the reviewer requests the pre-submission summary
    Then GitHub shows no posted comments
    And GitHub shows no posted reviews

  Scenario: A private note is never synced to GitHub on submission
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And GitHub sync is enabled for that Pull Request
    And the reviewer has requested the Change Map
    And the reviewer has posted the private note "Ask the author" scoped to the rename Change
    When the reviewer confirms and submits the review
    Then GitHub shows no posted comments

  Scenario: Submitting without connecting to GitHub fails
    Given the reviewer has not connected to GitHub
    When the reviewer confirms and submits the review
    Then the request is rejected as unauthorized

  Scenario: Submitting before selecting a PR fails
    Given the reviewer is connected to GitHub
    But the reviewer has not selected a PR
    When the reviewer confirms and submits the review
    Then the request is rejected because no PR is selected
