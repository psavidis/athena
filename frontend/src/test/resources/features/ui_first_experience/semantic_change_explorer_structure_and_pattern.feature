Feature: Semantic Change Explorer — Structure and Pattern level views
  The Structure and Pattern levels of the Semantic Change Explorer (ticket
  #95's shell) give a reviewer dedicated visualizations for the two most
  granular levels of the semantic spine: Structure shows the atomic
  changes a Change is made of, and Pattern shows which recognizable
  implementation technique those atomic changes add up to, so the
  reviewer can go from raw diff to design intent without leaving the
  Explorer (ticket #91 §3/§4).

  Scenario: The Structure level shows one chip per structural change
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Structure level is classified with the structural changes "Rename Method" and "Add Parameter"
    When the reviewer selects the Structure level on the spine
    Then the center stage shows one chip for "Rename Method" and one chip for "Add Parameter"

  Scenario: Selecting a structural chip highlights its diff evidence
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Structure level is classified with the structural changes "Rename Method" and "Add Parameter"
    When the reviewer selects the "Rename Method" chip
    Then the evidence panel highlights the diff evidence for "Rename Method"
    And the diff evidence for "Add Parameter" is not highlighted

  Scenario: Structural chips are always shown as Observed
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Structure level is classified with the structural change "Rename Method"
    When the reviewer selects the Structure level on the spine
    Then the "Rename Method" chip shows an Observed confidence at 100%

  Scenario: The Structure level shows an empty state when the Change has no structural classification
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Structure level has no classification
    When the reviewer selects the Structure level on the spine
    Then the center stage shows that the Structure level has no classification for this Change

  Scenario: The Pattern level shows a recognized pattern as a hero card
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Pattern level is classified as "Dependency Injection"
    When the reviewer selects the Pattern level on the spine
    Then the center stage shows "Dependency Injection" as a hero card

  Scenario: The Pattern hero card lists the structural changes that support it
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Pattern level is classified as "Dependency Injection", supported by the structural changes "Add Constructor Parameter" and "Remove Field"
    When the reviewer selects the Pattern level on the spine
    Then the "Dependency Injection" hero card lists "Add Constructor Parameter" and "Remove Field" as supporting structural changes

  Scenario: Selecting a supporting structural change under a pattern links back to the Structure level
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Pattern level is classified as "Dependency Injection", supported by the structural change "Add Constructor Parameter"
    When the reviewer selects "Add Constructor Parameter" under the "Dependency Injection" pattern
    Then the Structure level is indicated as the current level
    And the center stage highlights the "Add Constructor Parameter" chip

  Scenario: The Pattern hero card shows its confidence as Inferred with a percentage
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Pattern level is classified as "Dependency Injection" with 70% confidence
    When the reviewer selects the Pattern level on the spine
    Then the "Dependency Injection" hero card shows an Inferred confidence at 70%

  Scenario: The Pattern level shows an empty state when the Change embodies no recognizable pattern
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Pattern level has no classification
    When the reviewer selects the Pattern level on the spine
    Then the center stage shows that the Pattern level has no classification for this Change

  Scenario: The Pattern level shows one hero card per recognized pattern when a Change embodies more than one
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Pattern level is classified as both "Dependency Injection" and "Factory"
    When the reviewer selects the Pattern level on the spine
    Then the center stage shows a hero card for "Dependency Injection" and a hero card for "Factory"
