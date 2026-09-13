Feature: Review state, pre-submission summary & submission rendering (frontend)
  A reviewer sets a Change's review state from the Change Map, opens the
  pre-submission summary, and confirms submission to GitHub, all from the
  browser (ticket #76).

  Scenario: A reviewer sets a Change's review state from the Change Map
    Given the reviewer is viewing the Change Map with a Change described as "Rename greet to salute"
    When the reviewer sets that Change's review state to "Reviewed"
    Then the Change Map shows that Change's review state as "Reviewed"

  Scenario: A reviewer opens the pre-submission summary
    Given the reviewer is viewing the Change Map
    When the reviewer opens the pre-submission summary
    Then the summary shows the reviewed Change count
    And the summary shows the comment count
    And the summary shows the previewed GitHub action

  Scenario: A reviewer confirms submission and sees the result
    Given the reviewer is viewing the pre-submission summary
    When the reviewer confirms submission
    Then the reviewer sees that the submission succeeded

  Scenario: A reviewer can cancel out of the pre-submission summary without submitting
    Given the reviewer is viewing the pre-submission summary
    When the reviewer navigates back without confirming
    Then the reviewer sees the Change Map again
