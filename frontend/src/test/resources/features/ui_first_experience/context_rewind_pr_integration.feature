Feature: Context Rewind — PR review integration
  Invoking Rewind on a changed entity while reviewing a PR (ticket #194)
  does not feel like leaving the review for a separate "history mode":
  the canvas underneath is preserved exactly as the reviewer left it —
  camera position and all — and a single action always returns to it.
  Reuses the Rewind entry point already wired in ticket #187; this ticket
  is about how it's presented (an overlay over the still-mounted canvas,
  the same pattern the detail drawer already uses), not new Context
  Rewind content.

  Scenario: Invoking Rewind does not move or resize the canvas underneath it
    Given the reviewer has panned the canvas to a specific position while reviewing a PR
    When the reviewer invokes Rewind on a changed file
    Then the canvas's pan position is unchanged underneath

  Scenario: A single action returns from Context Rewind to exactly where the reviewer left the PR
    Given the reviewer has invoked Rewind on a changed file, panning the canvas beforehand
    When the reviewer selects "← Back" in Context Rewind
    Then Context Rewind is no longer visible
    And the canvas's pan position is still unchanged
