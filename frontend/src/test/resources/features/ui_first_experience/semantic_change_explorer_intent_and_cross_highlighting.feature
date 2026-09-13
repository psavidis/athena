Feature: Semantic Change Explorer — Intent level and semantic cross-highlighting
  The Intent level of the Semantic Change Explorer (ticket #95's shell)
  gives a reviewer the inferred reason a Change was made, with the
  evidence backing that inference and any lower-confidence alternative
  readings shown distinctly, so Intent never reads as more certain than
  it is (ticket #91 §9). Semantic cross-highlighting makes selecting any
  classified concept — anywhere in the Explorer — highlight the other
  classifications (across dimensions) that share its evidence, so the
  interface reads as one connected explanation (ticket #91 §10).

  Scenario: The Intent level shows the primary inferred reason with a "Why?" framing
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Intent level is classified as "Reduce Coupling"
    When the reviewer selects the Intent level on the spine
    Then the center stage shows "Reduce Coupling" as the primary inferred reason

  Scenario: The Intent level lists the specific evidence backing the inference
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Intent level is classified as "Reduce Coupling", backed by the evidence "Dependency is now supplied through the constructor"
    When the reviewer selects the Intent level on the spine
    Then the center stage lists "Dependency is now supplied through the constructor" as supporting evidence

  Scenario: The Intent level shows an alternative reading distinctly, at a lower confidence than the primary
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Intent level is classified as "Reduce Coupling" primary with "Improve Maintainability" as an alternative reading
    When the reviewer selects the Intent level on the spine
    Then "Improve Maintainability" is shown as an alternative reading, distinct from the primary
    And the alternative reading's confidence is lower than the primary reading's confidence

  Scenario: The Intent level shows an empty state when the Change has no intent classification
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Intent level has no classification
    When the reviewer selects the Intent level on the spine
    Then the center stage shows that the Intent level has no classification for this Change

  Scenario: Every inferred level exposes a "Show evidence" affordance
    Given the reviewer is viewing the Semantic Change Explorer for a Change with an Inferred classification on the Pattern level
    When the reviewer selects the Pattern level on the spine
    Then the reviewer can see that level's supporting evidence

  Scenario: Selecting a classified concept highlights other classifications that share its evidence
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Pattern and Framework levels are both classified from the same underlying evidence
    When the reviewer selects the Pattern classification
    Then the Framework classification that shares its evidence is shown highlighted

  Scenario: Selecting a classified concept subdues unrelated content rather than removing it
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Pattern and Framework levels are both classified from the same underlying evidence, and whose Capability level is classified from unrelated evidence
    When the reviewer selects the Pattern classification
    Then the Capability classification is shown subdued, not hidden

  Scenario: Clicking a bracketed term in the Change Story propagates a real highlight
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Pattern and Framework levels are both classified from the same underlying evidence
    When the reviewer clicks the Pattern term in the Change Story
    Then the Framework classification that shares its evidence is shown highlighted
