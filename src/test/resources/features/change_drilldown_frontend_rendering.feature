Feature: Change drill-down, comments & private notes rendering (frontend)
  Clicking a Change in the Change Map opens its detail view in the browser,
  where the reviewer can read its category, description, symbols, files,
  and diff, and attach comments or private notes (ticket #75).

  Scenario: Clicking a Change in the Change Map opens its detail view
    Given the reviewer is viewing the Change Map with a Change described as "Rename greet to salute"
    When the reviewer clicks that Change
    Then the detail view shows the description "Rename greet to salute"
    And the detail view shows the Change's category
    And the detail view lists the involved symbols
    And the detail view lists the touched files
    And the detail view shows the underlying diff

  Scenario: A reviewer attaches a comment to the open Change
    Given the reviewer is viewing a Change's detail view
    When the reviewer submits the comment "Good refactor" at Change scope
    Then the detail view's comments list shows "Good refactor"

  Scenario: A reviewer attaches a private note that is visually distinct from a comment
    Given the reviewer is viewing a Change's detail view
    When the reviewer submits the private note "Ask the author about this" at Change scope
    Then the detail view's private notes list shows "Ask the author about this"
    And the private note is styled distinctly from a comment

  Scenario: A blank comment cannot be submitted
    Given the reviewer is viewing a Change's detail view
    When the reviewer attempts to submit a blank comment
    Then the submission is blocked without a request to the server

  Scenario: The reviewer can navigate back to the Change Map from a Change's detail view
    Given the reviewer is viewing a Change's detail view
    When the reviewer navigates back
    Then the reviewer sees the Change Map again
