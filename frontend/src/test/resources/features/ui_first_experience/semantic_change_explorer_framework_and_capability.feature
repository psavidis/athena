Feature: Semantic Change Explorer — Framework and Capability level views
  The Framework and Capability levels of the Semantic Change Explorer
  (ticket #95's shell) give a reviewer dedicated visualizations for the
  two most business-facing levels of the semantic spine: Framework shows
  which framework/platform mechanism is at play, and Capability shows
  which product responsibility the change actually affects, so the
  reviewer can connect a diff to its business meaning without leaving the
  Explorer (ticket #91 §5/§6).

  Scenario: The Framework level shows the identified mechanism
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Framework level is classified as "Spring Constructor Injection"
    When the reviewer selects the Framework level on the spine
    Then the center stage shows "Spring Constructor Injection" as the framework mechanism

  Scenario: A Framework mechanism transition shows an explicit before/after
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Framework level is classified as a transition from "@Autowired field injection" to "constructor injection"
    When the reviewer selects the Framework level on the spine
    Then the center stage shows "@Autowired field injection" as the before mechanism
    And the center stage shows "constructor injection" as the after mechanism

  Scenario: A Framework classification with no prior mechanism shows only the current mechanism
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Framework level is classified as "JPA Entity Mapping" with no prior mechanism
    When the reviewer selects the Framework level on the spine
    Then the center stage shows "JPA Entity Mapping" as the current mechanism
    And the center stage does not show a before/after split

  Scenario: Framework classifications are always shown as Observed
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Framework level is classified as "Jackson Serialization"
    When the reviewer selects the Framework level on the spine
    Then the "Jackson Serialization" mechanism shows an Observed confidence at 100%

  Scenario: The Framework level shows an empty state when the Change has no framework classification
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Framework level has no classification
    When the reviewer selects the Framework level on the spine
    Then the center stage shows that the Framework level has no classification for this Change

  Scenario: The Capability level shows the affected responsibility in human-readable terms
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Capability level is classified as "Process Payment"
    When the reviewer selects the Capability level on the spine
    Then the center stage shows "Process Payment" as a capability card

  Scenario: Selecting a capability card shows its supporting code evidence
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Capability level is classified as "Process Payment"
    When the reviewer selects the "Process Payment" capability card
    Then the evidence panel shows the code evidence for "Process Payment"

  Scenario: The Capability level shows separate cards when a Change touches more than one capability
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Capability level is classified as both "Process Payment" and "Validate Card"
    When the reviewer selects the Capability level on the spine
    Then the center stage shows a capability card for "Process Payment"
    And the center stage shows a separate capability card for "Validate Card"
    And each capability card is individually selectable

  Scenario: Capability classifications are shown as Inferred with a confidence percentage
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Capability level is classified as "Add User" with 82% confidence
    When the reviewer selects the Capability level on the spine
    Then the "Add User" capability card shows an Inferred confidence at 82%

  Scenario: The Capability level shows an empty state when the Change has no responsibility classification
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Capability level has no classification
    When the reviewer selects the Capability level on the spine
    Then the center stage shows that the Capability level has no classification for this Change
