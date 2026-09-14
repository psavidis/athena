Feature: Semantic Canvas — sliding detail drawer
  Selecting anything on the Semantic Canvas opens a bottom sliding drawer
  with that selection's detail (ticket #131), instead of the retired
  Explorer's permanently-occupying evidence panel — the drawer is closed
  by default and never disturbs the reviewer's pan/zoom position.

  Scenario: The drawer is closed by default
    Given the reviewer is viewing the Semantic Canvas territory map
    Then the detail drawer is not visible

  Scenario: Selecting a concept node opens the drawer with its description and linked chips
    Given the reviewer is viewing a territory whose Pattern level is classified as "Idempotent recovery" touching "PaymentValidator.java"
    When the reviewer selects the "Idempotent recovery" concept node
    Then the detail drawer opens showing "Idempotent recovery"'s description
    And the drawer shows a "PaymentValidator.java" chip

  Scenario: Clicking a linked chip in the drawer jumps elsewhere on the canvas
    Given the reviewer has the detail drawer open for a concept with a "PaymentValidator.java" file chip
    When the reviewer clicks the "PaymentValidator.java" chip
    Then the canvas focuses on "PaymentValidator.java"

  Scenario: Selecting a file node opens the drawer with its diff and an evidence statement
    Given the reviewer is viewing the Structure stop for a territory whose "PaymentValidator.java" file belongs to the "Idempotent recovery" concept
    When the reviewer selects the "PaymentValidator.java" file node
    Then the detail drawer opens showing the diff for "PaymentValidator.java"
    And the drawer shows a one-line statement tying "PaymentValidator.java" back to "Idempotent recovery"

  Scenario: Selecting the PR-overview node shows a footprint summary instead of a diff
    Given the reviewer is viewing the Semantic Canvas for a PR touching "crowdness-live" and "crowdness-ingestion"
    When the reviewer selects the PR-overview node
    Then the detail drawer opens showing a footprint summary with a proportional bar of files changed per module
    And the drawer does not show a diff

  Scenario: Closing the drawer clears the selection without resetting the camera
    Given the reviewer has the detail drawer open while the camera is panned and zoomed into a territory
    When the reviewer closes the detail drawer
    Then the detail drawer is no longer visible
    And the camera's pan and zoom position is unchanged

  Scenario: Opening the drawer does not move or resize the canvas underneath it
    Given the reviewer has panned and zoomed the canvas to a specific position
    When the reviewer selects a node that opens the detail drawer
    Then the canvas's pan and zoom position is unchanged by the drawer opening
