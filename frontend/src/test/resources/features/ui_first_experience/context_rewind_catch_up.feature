Feature: Context Rewind — "Catch me up" since a chosen date
  Context Rewind (ticket #189) lets a developer scope an entity's history
  to only what happened since a date they pick, instead of manually
  inspecting months of Git history. There is no automatic "you were last
  here on..." detection anywhere in Athena today (no per-developer
  last-interaction tracking exists), so "catch me up" is a developer-
  chosen date rather than an auto-detected one — a deliberate, disclosed
  descope from the original vision, not an oversight.

  Scenario: Catching up since a chosen date shows only the activity after it, with a count
    Given a commit on "2025-12-01" changed "PaymentProcessor"
    And a commit on "2026-02-01" separately changed "PaymentProcessor"
    When the developer asks Context Rewind to catch them up on "PaymentProcessor" since "2026-01-01"
    Then Context Rewind shows only the "2026-02-01" activity
    And it reports "1" activity since "2026-01-01"

  Scenario: Without catching up, Context Rewind still shows the full timeline
    Given a commit on "2025-12-01" changed "PaymentProcessor"
    And a commit on "2026-02-01" separately changed "PaymentProcessor"
    When the developer opens Context Rewind for "PaymentProcessor" without catching up
    Then Context Rewind shows both the "2025-12-01" and "2026-02-01" activity

  Scenario: Catching up to a date with no activity since then says so plainly
    Given a commit on "2025-12-01" changed "PaymentProcessor"
    When the developer asks Context Rewind to catch them up on "PaymentProcessor" since "2026-06-01"
    Then Context Rewind states that nothing has changed since "2026-06-01"
    And it does not render an empty timeline

  Scenario: Catching up without picking a date is blocked
    Given the developer is at Context Rewind's "catch me up" prompt for "PaymentProcessor"
    When the developer leaves the date blank
    Then the "Catch me up" action is disabled
