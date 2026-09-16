Feature: Keyboard shortcut discoverability
  A reviewer can discover Athena's keyboard interactions without leaving
  the keyboard — a shortcut reference is itself keyboard-navigable, and
  shortcuts are surfaced contextually where a reviewer would look for them
  (ticket #159).

  Scenario: Opening the keyboard shortcut reference with the keyboard
    Given the reviewer is anywhere in the review
    When the reviewer uses the keyboard shortcut to open the shortcut reference
    Then the shortcut reference view opens
    And keyboard focus moves into the shortcut reference view

  Scenario: Navigating the keyboard shortcut reference with the keyboard
    Given the reviewer has the shortcut reference view open, listing more than one shortcut group
    When the reviewer moves keyboard focus forward through the shortcut reference view
    Then keyboard focus moves between the listed shortcut groups

  Scenario: Closing the keyboard shortcut reference with the keyboard
    Given the reviewer has the shortcut reference view open
    When the reviewer uses the keyboard shortcut to close the current view
    Then the shortcut reference view closes
    And keyboard focus returns to wherever it was before the reference view opened

  Scenario: An action menu displays its keyboard shortcut
    Given an action menu item that has an assigned keyboard shortcut
    When the reviewer opens that action menu
    Then the menu item shows its keyboard shortcut alongside its label

  Scenario: A tooltip exposes a keyboard alternative to a mouse action
    Given a control that has an assigned keyboard shortcut
    When the reviewer views that control's tooltip
    Then the tooltip shows the control's keyboard shortcut
