Feature: Intent classification of code changes
  A reviewer needs a change to carry an inferred reason (its Intent),
  together with the evidence it was inferred from, so that the Semantic
  Change Explorer's Intent level (issue #91) can answer "why was this
  changed" instead of leaving that level empty.

  Intent is the most inferential of the semantic dimensions, so it is
  never guessed from the raw diff alone: it is only assigned when a
  change's own other semantic classifications (Pattern, Framework, and
  so on) correlate with a known reason for change.

  Scenario: A change whose other classifications correlate with a known intent is classified with that intent, its plausible alternatives, and the evidence it was inferred from
    Given a change already classified with the "Dependency Injection" concept along the Pattern dimension
    And the same change already classified with the "Spring: Field to Constructor Injection" concept along the Framework dimension
    When the change is classified along the Intent dimension
    Then its primary Intent classification is the "Reduce Coupling" concept
    And it also carries the "Improve Maintainability" concept as a lower-confidence alternative
    And that classification's evidence is the change's Pattern and Framework classifications, not its raw diff

  Scenario: A change with no correlating classification on any other dimension is left unclassified
    Given a change with no classification on any dimension other than Intent
    When the change is classified along the Intent dimension
    Then it has no Intent classification
