Feature: Keyboard navigation and focus model
  Every interactive surface of Athena — Changes, files, findings, comments,
  the Semantic Canvas, panels, and controls — is reachable and operable
  with the keyboard alone, with a visible focus indicator and no region
  that traps the keyboard (ticket #159).

  Scenario: Every kind of interactive element is keyboard-reachable
    Given a review containing a Change, a file, a finding, a comment, a Semantic Canvas node, and an open panel
    When the reviewer moves keyboard focus forward through the review
    Then keyboard focus visits the Change, the file, the finding, the comment, the Semantic Canvas node, and the panel's controls
    And no mouse interaction is required to reach any of them

  Scenario: The focused element always shows a visible focus indicator
    Given a reviewer navigating the review with the keyboard
    When keyboard focus moves to an interactive element
    Then that element shows a visible focus indicator
    And the previously focused element no longer shows one

  Scenario: Navigation order follows the semantic structure of the review
    Given a review whose visual layout order differs from its semantic review → file → change → finding → comment structure
    When the reviewer moves keyboard focus forward through the review
    Then focus follows the semantic review → file → change → finding → comment order
    And not the raw visual layout order

  Scenario: A reviewer can always leave a UI region with the keyboard
    Given the reviewer has moved keyboard focus into a bounded UI region such as a panel or the Semantic Canvas
    When the reviewer uses the keyboard shortcut to leave the current region
    Then keyboard focus moves to a control outside that region
    And the reviewer is not prevented from continuing to navigate the rest of the review

  Scenario: Keyboard navigation works the same after entering a context with the mouse
    Given the reviewer opened a Change's detail view by clicking it with the mouse
    When the reviewer continues navigating with the keyboard alone
    Then keyboard navigation behaves exactly as it would had the reviewer opened the detail view with the keyboard
