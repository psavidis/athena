Feature: Semantic Change Explorer — Flow and Architecture level views
  The Flow and Architecture levels of the Semantic Change Explorer (ticket
  #95's shell) give a reviewer dedicated visualizations for the two most
  structural-context levels of the semantic spine: Flow shows which
  use-case flow a Change belongs to, and Architecture shows which
  architectural role(s) the Change plays against the full set of
  recognized roles, so the reviewer sees context without noise (ticket
  #91 §7/§8).

  Scenario: The Flow level shows the affected use-case flow
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Flow level is classified as "Add User"
    When the reviewer selects the Flow level on the spine
    Then the center stage shows "Add User" as the affected flow

  Scenario: Flow classifications are always shown as Observed
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Flow level is classified as "Add User"
    When the reviewer selects the Flow level on the spine
    Then the "Add User" flow shows an Observed confidence at 100%

  Scenario: The Flow level shows an empty state when the Change has no flow classification
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Flow level has no classification
    When the reviewer selects the Flow level on the spine
    Then the center stage shows that the Flow level has no classification for this Change

  Scenario: The Architecture level shows the full set of recognized architectural roles as a layered stack
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Architecture level is classified as touching the "Driving Adapter" role
    When the reviewer selects the Architecture level on the spine
    Then the center stage shows every recognized architectural role in the layered stack

  Scenario: The Architecture level highlights the role the Change actually touches
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Architecture level is classified as touching the "Driving Adapter" role
    When the reviewer selects the Architecture level on the spine
    Then the "Driving Adapter" role is shown highlighted
    And the other roles are shown present but not highlighted

  Scenario: The Architecture level highlights more than one role when the Change touches more than one
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Architecture level is classified as touching both the "Driving Adapter" and "Domain Service" roles
    When the reviewer selects the Architecture level on the spine
    Then the "Driving Adapter" role is shown highlighted
    And the "Domain Service" role is shown highlighted

  Scenario: The Architecture level shows its confidence as Inferred with a percentage
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Architecture level is classified as touching the "Driving Adapter" role with 70% confidence
    When the reviewer selects the Architecture level on the spine
    Then the "Driving Adapter" role shows an Inferred confidence at 70%

  Scenario: The Architecture level shows an empty state when the Change has no architectural classification
    Given the reviewer is viewing the Semantic Change Explorer for a Change whose Architecture level has no classification
    When the reviewer selects the Architecture level on the spine
    Then the center stage shows that the Architecture level has no classification for this Change
