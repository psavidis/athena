Feature: Review Briefing overlay over the Semantic Canvas
  A developer opening a PR sees a compact Review Briefing overlay over
  the canvas, not a separate screen, and collapses it to a small
  indicator once they start reviewing — reopenable at any time (ticket
  #223). Reuses the overlay-over-canvas pattern Context Rewind's PR
  integration already established.

  Scenario: A developer sees the briefing overlay on first entering a PR
    Given a developer opens a PR with a Review Briefing available
    Then the Review Briefing overlay is shown

  Scenario: Starting the review collapses the overlay to an indicator
    Given a developer has the Review Briefing overlay open
    When the developer starts the review
    Then the Review Briefing overlay is collapsed to an indicator

  Scenario: The collapsed indicator reopens the overlay
    Given a developer has collapsed the Review Briefing to an indicator
    When the developer selects the Review Briefing indicator
    Then the Review Briefing overlay is shown

  Scenario: A different PR shows the overlay again on entry
    Given a developer has collapsed the Review Briefing to an indicator for one PR
    When the developer opens a different PR with a Review Briefing available
    Then the Review Briefing overlay is shown
