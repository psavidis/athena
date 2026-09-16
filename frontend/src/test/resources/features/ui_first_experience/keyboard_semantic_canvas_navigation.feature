Feature: Keyboard navigation of the Semantic Canvas
  A reviewer moves through the Semantic Canvas's territories and nodes,
  selects them, dives into and out of their context, and moves between
  siblings, all from the keyboard and along the same semantic hierarchy
  the canvas exposes visually (ticket #159).

  Scenario: Moving keyboard focus between semantic elements on the canvas
    Given the reviewer is viewing the Semantic Canvas territory map with more than one territory
    When the reviewer uses the keyboard shortcut to move to the next semantic element
    Then keyboard focus moves to the next territory on the canvas

  Scenario: Selecting a semantic element with the keyboard
    Given the reviewer has keyboard focus on the "Idempotent recovery" concept node
    When the reviewer uses the keyboard shortcut to select the focused element
    Then the detail drawer opens showing "Idempotent recovery"'s description

  Scenario: Diving into a territory's context with the keyboard
    Given the reviewer has keyboard focus on the "crowdness-live" territory
    When the reviewer uses the keyboard shortcut to enter the focused element's context
    Then the camera dives into the "crowdness-live" territory
    And keyboard focus moves to a semantic element inside that territory

  Scenario: Leaving a territory's context back to its parent with the keyboard
    Given the reviewer has dived into the "crowdness-live" territory using the keyboard
    When the reviewer uses the keyboard shortcut to leave the current element's context
    Then the camera returns to the territory map
    And keyboard focus returns to the "crowdness-live" territory

  Scenario: Moving between sibling semantic elements with the keyboard
    Given the reviewer has keyboard focus on a component inside the "crowdness-live" territory, which has more than one component
    When the reviewer uses the keyboard shortcut to move to the next sibling element
    Then keyboard focus moves to the next component inside the same territory

  Scenario: Jumping to a higher-level representation with the keyboard
    Given the reviewer has keyboard focus on a component classified at the Pattern+Framework level
    When the reviewer uses the keyboard shortcut to move to the higher-level representation
    Then keyboard focus moves to that component's Architecture-level representation

  Scenario: Jumping to a lower-level representation with the keyboard
    Given the reviewer has keyboard focus on a territory classified at the Architecture level
    When the reviewer uses the keyboard shortcut to move to the lower-level representation
    Then keyboard focus moves to that territory's Pattern+Framework-level representation
