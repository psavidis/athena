Feature: Review Context artifact assembly and pre-submission summary
  Everything a reviewer did while forming their understanding accumulates
  into a structured Review Context artifact, and before anything is sent
  to GitHub the reviewer sees a pre-submission summary and must explicitly
  confirm submission (epic #6 §7, §32, §33).

  Scenario: The Review Context reports reviewed, skipped, and mechanical Changes separately
    Given a PR titled "Move authentication to Account" with a rename Change and a mechanical replacement Change
    And the reviewer has reviewed the rename Change for the review context
    And the reviewer has marked the mechanical replacement Change as mechanical
    When the reviewer assembles the Review Context
    Then the Review Context's reviewed Changes include the rename Change
    And the Review Context's mechanical Changes include the mechanical replacement Change

  Scenario: The Review Context includes comments but excludes private notes by default
    Given a PR titled "Move authentication to Account" with a rename Change and a mechanical replacement Change
    And a review-context comment "Looks good" attached to the rename Change
    And a review-context private note "Not fully sure, ask the author" attached to the rename Change
    When the reviewer assembles the Review Context
    Then the Review Context's comments include "Looks good"
    And the Review Context's private notes are empty

  Scenario: A reviewer can explicitly opt private notes into the Review Context
    Given a PR titled "Move authentication to Account" with a rename Change and a mechanical replacement Change
    And a review-context private note "Not fully sure, ask the author" attached to the rename Change
    When the reviewer assembles the Review Context including private notes
    Then the Review Context's private notes include "Not fully sure, ask the author"

  Scenario: The Review Context reports semantic review coverage
    Given a PR titled "Move authentication to Account" with a rename Change and a mechanical replacement Change
    And the reviewer has reviewed the rename Change for the review context
    When the reviewer assembles the Review Context
    Then the Review Context's coverage summary reports exactly 1 Change reviewed out of the total detected

  Scenario: The pre-submission summary shows reviewed, mechanical, concerns, and comment count
    Given a PR titled "Move authentication to Account" with a rename Change and a mechanical replacement Change
    And the reviewer has reviewed the rename Change for the review context
    And the reviewer has marked the mechanical replacement Change as mechanical
    And a review-context comment "Looks good" attached to the rename Change
    When the reviewer opens the pre-submission summary
    Then the summary lists the rename Change as reviewed
    And the summary lists the mechanical replacement Change as classified mechanical
    And the summary reports 1 comment

  Scenario: Submission requires explicit confirmation
    Given a PR titled "Move authentication to Account" with a rename Change and a mechanical replacement Change
    When the reviewer attempts to submit without confirming
    Then the submission is not sent

  Scenario: An explicitly confirmed submission succeeds
    Given a PR titled "Move authentication to Account" with a rename Change and a mechanical replacement Change
    When the reviewer confirms and submits the review
    Then the submission is sent
