Feature: Focus management across context changes
  Whenever an action in Athena changes context — opening an editor, a
  panel, or a different Semantic Canvas level — keyboard focus moves
  explicitly and predictably, so the reviewer always knows where keyboard
  interaction currently applies (ticket #159).

  Scenario: Opening a comment editor moves focus into it
    Given the reviewer has keyboard focus on a Change
    When the reviewer opens a comment editor on that Change
    Then keyboard focus is inside the comment editor

  Scenario: Submitting a comment returns focus to the review context
    Given the reviewer has an open comment editor on a Change
    When the reviewer submits the comment
    Then keyboard focus returns to that Change

  Scenario: Cancelling a comment returns focus to the review context
    Given the reviewer has an open comment editor on a Change
    When the reviewer cancels the comment
    Then keyboard focus returns to that Change

  Scenario: Opening a panel moves focus into it
    Given the reviewer has keyboard focus on a control that opens a panel
    When the reviewer opens the panel
    Then keyboard focus is inside the panel

  Scenario: Closing a panel returns focus to the control that opened it
    Given the reviewer has an open panel that was opened from a specific control
    When the reviewer closes the panel
    Then keyboard focus returns to the control that opened it

  Scenario: Changing the Semantic Canvas's zoom level preserves a meaningful focus target
    Given the reviewer has keyboard focus on a component inside a territory
    When the reviewer zooms out to the territory map
    Then keyboard focus moves to that component's owning territory
    And keyboard focus is never lost to no element at all

  Scenario: Diving into a territory preserves a meaningful focus target
    Given the reviewer has keyboard focus on a territory
    When the reviewer dives into that territory
    Then keyboard focus moves to a semantic element inside that territory
    And keyboard focus is never lost to no element at all
