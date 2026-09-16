Feature: Keyboard review navigation
  A reviewer moves through the Review Queue's Changes, a Change's files,
  AI findings, and comments — and back to whatever context they came from
  — entirely from the keyboard (ticket #159), without needing to be
  reminded which item the mouse last touched.

  Scenario: Moving to the next Change in the Review Queue
    Given the reviewer is focused on a Change in the Review Queue
    When the reviewer uses the keyboard shortcut for the next Change
    Then keyboard focus moves to the next Change in the Review Queue's order

  Scenario: Moving to the previous Change in the Review Queue
    Given the reviewer is focused on the second Change in the Review Queue
    When the reviewer uses the keyboard shortcut for the previous Change
    Then keyboard focus moves to the first Change in the Review Queue's order

  Scenario: There is no next Change after the last one
    Given the reviewer is focused on the last Change in the Review Queue
    When the reviewer uses the keyboard shortcut for the next Change
    Then keyboard focus remains on the last Change

  Scenario: Entering a Change's detail view with the keyboard
    Given the reviewer is focused on a Change in the Review Queue
    When the reviewer uses the keyboard shortcut to enter the focused Change
    Then the Change's detail view opens
    And keyboard focus moves into the detail view

  Scenario: Returning from a Change's detail view to the Review Queue
    Given the reviewer opened a Change's detail view from the Review Queue using the keyboard
    When the reviewer uses the keyboard shortcut to return to the previous context
    Then the Change's detail view closes
    And keyboard focus returns to that Change in the Review Queue

  Scenario: Moving to the next file within a Change's detail view
    Given the reviewer is viewing a Change's detail view listing more than one touched file
    When the reviewer uses the keyboard shortcut for the next file
    Then keyboard focus moves to the next listed file

  Scenario: Moving to the next AI finding
    Given the reviewer is focused on an AI finding
    When the reviewer uses the keyboard shortcut for the next finding
    Then keyboard focus moves to the next AI finding

  Scenario: Jumping from a finding to its related Change
    Given the reviewer is focused on an AI finding about a specific Change
    When the reviewer uses the keyboard shortcut to jump to the finding's related Change
    Then the Change's detail view opens
    And keyboard focus moves into the detail view

  Scenario: A finding with no related Change has no jump target
    Given the reviewer is focused on an AI finding with no related Change
    When the reviewer uses the keyboard shortcut to jump to the finding's related Change
    Then keyboard focus does not move
