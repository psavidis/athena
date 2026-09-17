Feature: Review Briefing — keyboard-first interaction
  A developer opens, navigates, and acts on the entire Review Briefing
  without a mouse, using Athena's existing keyboard-first conventions
  (ticket #159): j/k to move between items, Enter to select, Escape to
  close. Selecting an item focuses the Semantic Canvas the same way a
  mouse click already does (ticket #224).

  Scenario: A developer reopens the collapsed indicator with a keyboard shortcut
    Given a developer has collapsed the Review Briefing to an indicator
    And the indicator has keyboard focus
    When the developer presses Enter
    Then the Review Briefing overlay is shown

  Scenario: A developer closes the overlay with a keyboard shortcut
    Given a developer has the Review Briefing overlay open
    When the developer presses Escape
    Then the Review Briefing overlay is collapsed to an indicator

  Scenario: A developer moves focus to the next briefing item
    Given a developer has the Review Briefing overlay open with a focus area and a question
    And the focus area has keyboard focus
    When the developer presses the next-item key
    Then the question has keyboard focus

  Scenario: A developer moves focus to the previous briefing item
    Given a developer has the Review Briefing overlay open with a focus area and a question
    And the question has keyboard focus
    When the developer presses the previous-item key
    Then the focus area has keyboard focus

  Scenario: A developer selects the focused item with a keyboard shortcut
    Given a developer has the Review Briefing overlay open with a focus area about the "OrderService" entity
    And "OrderService" lives in the "orders" module
    And the focus area has keyboard focus
    When the developer presses Enter
    Then the Semantic Canvas is focused on the "orders" module

  Scenario: A developer starts the review with a keyboard shortcut
    Given a developer has the Review Briefing overlay open
    And the Start Review control has keyboard focus
    When the developer presses Enter
    Then the Review Briefing overlay is collapsed to an indicator
