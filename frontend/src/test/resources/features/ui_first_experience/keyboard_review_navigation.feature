Feature: Keyboard review navigation
  A reviewer moves through the touched files/concepts the Semantic Canvas
  surfaces for the current territory, a Change's own detail view, AI
  findings, and comments — and back to whatever context they came from —
  entirely from the keyboard (ticket #159), without needing to be
  reminded which item the mouse last touched.

  Note: earlier drafting of this file assumed a separate "Review Queue"
  list view. That view doesn't exist in this app — the Semantic Canvas's
  territory/node navigation replaced it (see `ChangeDetailPage`'s own
  note that the flat "Change Map" is retired). Navigation here is
  reframed onto the canvas's real touched-item model.

  Scenario: Moving to the next touched item in the current territory
    Given the reviewer is focused on a touched file/concept node in the current territory
    When the reviewer uses the keyboard shortcut for the next item
    Then keyboard focus moves to the next touched item in that territory

  Scenario: Moving to the previous touched item in the current territory
    Given the reviewer is focused on the second touched item in the current territory
    When the reviewer uses the keyboard shortcut for the previous item
    Then keyboard focus moves to the first touched item in that territory

  Scenario: There is no next item after the last one
    Given the reviewer is focused on the last touched item in the current territory
    When the reviewer uses the keyboard shortcut for the next item
    Then keyboard focus remains on the last item

  Scenario: Entering a touched item's Change detail view with the keyboard
    Given the reviewer is focused on a file node backed by a Change
    When the reviewer uses the keyboard shortcut to enter the focused item
    Then that Change's detail view opens
    And keyboard focus moves into the detail view

  Scenario: Returning from a Change's detail view to the canvas
    Given the reviewer opened a Change's detail view from the canvas using the keyboard
    When the reviewer uses the keyboard shortcut to return to the previous context
    Then the Change's detail view closes
    And keyboard focus returns to that item on the canvas

  Scenario: Moving to the next file listed in a Change's detail view
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
    Then that Change's detail view opens
    And keyboard focus moves into the detail view

  Scenario: A finding with no related Change has no jump target
    Given the reviewer is focused on an AI finding with no related Change
    When the reviewer uses the keyboard shortcut to jump to the finding's related Change
    Then keyboard focus does not move
