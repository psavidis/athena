Feature: Semantic Change Explorer — purposeful motion and animation pass
  The Semantic Change Explorer (tickets #95-#100) already lets a reviewer
  navigate a Change's meaning through the seven-level spine, cross-highlight
  related classifications, and move through Guided Review. This adds motion
  on top of those already-functional interactions so that relationships the
  reviewer already gets in static form now also *read* as connected: moving
  between levels feels like zooming into the same change rather than a page
  swap, hovering a classification previews its evidence, and focusing a
  concept subdues (not hides) what's unrelated (ticket #91 §15). Motion is
  additive polish — it never blocks or delays reaching the evidence/code,
  and it respects the reviewer's reduced-motion preference.

  Scenario: Entering the Pattern level clusters its supporting structural changes into the pattern
    Given the reviewer is viewing the Structure level of the Semantic Change Explorer for a Change whose Pattern level is classified as "Dependency Injection"
    When the reviewer selects the Pattern level on the spine
    Then the structural changes supporting "Dependency Injection" are shown clustered under it
    And the reviewer can still identify each individual structural change within the cluster

  Scenario: Moving between semantic levels transitions the same change rather than reloading the page
    Given the reviewer is viewing the Pattern level of the Semantic Change Explorer for a Change
    When the reviewer selects the Framework level on the spine
    Then the center stage transitions to the Framework level's content
    And the reviewer's place in the Explorer (spine, evidence panel) is preserved across the transition

  Scenario: Hovering a semantic element previews its supporting evidence
    Given the reviewer is viewing a level of the Semantic Change Explorer whose current classification has supporting evidence
    When the reviewer hovers that classification
    Then its supporting evidence is momentarily emphasized in the evidence panel
    And the emphasis clears when the reviewer stops hovering

  Scenario: Selecting a Capability transitions the Flow highlight into view
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Capability level is classified as "Add User" and whose Flow level is classified as "Register User"
    When the reviewer selects the "Add User" capability
    Then the "Register User" flow is shown highlighted
    And the highlight appears as a transition rather than an instant state change

  Scenario: Selecting an architectural role transitions its connected components into view
    Given the reviewer is viewing the Architecture level of the Semantic Change Explorer for a Change whose architecture includes a "Driven Adapter" connected to an "Outbound Port"
    When the reviewer selects the "Outbound Port" role
    Then the connected "Driven Adapter" is shown highlighted
    And the highlight appears as a transition rather than an instant state change

  Scenario: Focusing a semantic concept gradually subdues unrelated content
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Pattern and Framework levels are classified from the same underlying evidence, and whose Capability level is classified from unrelated evidence
    When the reviewer selects the Pattern classification
    Then the Capability classification transitions to a subdued appearance rather than disappearing immediately

  Scenario: A reviewer with reduced motion enabled sees the same information without animated transitions
    Given the reviewer has requested reduced motion
    And the reviewer is viewing the Semantic Change Explorer for a Change
    When the reviewer selects a different level on the spine
    Then the center stage shows that level's content immediately, without a transition animation
    And no information available with motion enabled is missing

  Scenario: Motion never delays a reviewer from reaching the evidence
    Given the reviewer is viewing the Semantic Change Explorer for a Change mid-way through a level transition
    When the reviewer opens the evidence panel for the current level
    Then the reviewer can view the supporting evidence immediately
    And no pending animation blocks the evidence from being shown
