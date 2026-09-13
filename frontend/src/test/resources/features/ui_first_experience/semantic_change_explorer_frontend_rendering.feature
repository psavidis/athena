Feature: Semantic Change Explorer rendering (frontend)
  Once a reviewer opens a Change's Semantic Change Explorer, the browser
  renders its seven-level semantic spine, an interactive Change Story
  sentence, and an always-visible evidence panel, so the reviewer can
  navigate the change's meaning instead of only reading a flat diff
  (ticket #95).

  Scenario: The semantic spine shows all seven levels with the current level indicated
    Given the reviewer is viewing the Semantic Change Explorer for a Change
    Then the spine shows the levels Structure, Pattern, Framework, Capability, Flow, Architecture, and Intent
    And the Structure level is indicated as the current level

  Scenario: Selecting a spine level updates the center stage without leaving the Explorer
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Pattern level is classified as "Dependency Injection"
    When the reviewer selects the Pattern level on the spine
    Then the center stage shows the Pattern level's content
    And the Pattern level is indicated as the current level
    And the reviewer is still viewing the Semantic Change Explorer

  Scenario: The Change Story sentence reflects the Change's actual classifications
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Pattern level is classified as "Dependency Injection" and whose Intent level is classified as "Improve Testability"
    Then the Change Story includes "Dependency Injection"
    And the Change Story includes "Improve Testability"

  Scenario: Clicking an element in the Change Story selects its level and updates the evidence panel
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Pattern level is classified as "Dependency Injection"
    When the reviewer clicks "Dependency Injection" in the Change Story
    Then the Pattern level is indicated as the current level
    And the evidence panel shows the evidence supporting that classification

  Scenario: The evidence panel shows the underlying diff for the current level's selection
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Structure level is classified as "Rename"
    Then the evidence panel shows the underlying diff for that Change

  Scenario: A level with no classification shows a minimal empty state instead of fabricated content
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Flow level has no classification
    When the reviewer selects the Flow level on the spine
    Then the center stage shows that the Flow level has no classification for this Change

  Scenario: The reviewer can exit the PR from the Semantic Change Explorer
    Given the reviewer is viewing the Semantic Change Explorer for a Change
    When the reviewer clicks the Athena wordmark
    Then the reviewer leaves the PR
