Feature: The same structural change repeated across classes is shown once
  A migration applied to many classes — the same member removed, or the same
  constructor parameter added, in each — reads as one entry with its count in
  the PR- and module-level Explorer, not one entry per class (ticket #291;
  #258 re-run report, improvement #4).

  Scenario: The same method removed from several classes is one entry
    Given the reviewer has selected a PR where method "setBeanFactory" was removed from classes "RegistrarA", "RegistrarB" and "RegistrarC"
    When the reviewer opens the PR-level semantic profile
    Then the Structural entries include one "Remove setBeanFactory in 3 classes" entry folding 3 changes
    And that entry names the classes "RegistrarA, RegistrarB, RegistrarC"

  Scenario: The same constructor parameter added to several classes is one entry
    Given the reviewer has selected a PR where constructor parameter "beanFactory" was added to classes "RegistrarA" and "RegistrarB"
    When the reviewer opens the PR-level semantic profile
    Then the Structural entries include one "Add Constructor Parameter beanFactory in 2 classes" entry folding 2 changes

  Scenario: A change made in only one class keeps its own entry
    Given the reviewer has selected a PR where method "setBeanFactory" was removed from classes "RegistrarA", "RegistrarB" and "RegistrarC", and method "setEnvironment" from "RegistrarA" only
    When the reviewer opens the PR-level semantic profile
    Then no Structural entry mentions "setEnvironment in"

  Scenario: The same member name under different structural changes is not folded together
    Given the reviewer has selected a PR where method "setBeanFactory" was removed from class "RegistrarA" and added to class "RegistrarB"
    When the reviewer opens the PR-level semantic profile
    Then no Structural entry mentions "setBeanFactory in"

  Scenario: The module-level profile folds repeated changes the same way
    Given the reviewer has selected a PR where method "setBeanFactory" was removed from classes "RegistrarA", "RegistrarB" and "RegistrarC"
    When the reviewer opens the semantic profile of module "core"
    Then the Structural entries include one "Remove setBeanFactory in 3 classes" entry folding 3 changes

  # Ticket #313: modifier changes fold by the change itself, not by member name.

  Scenario: The same modifier change made in several classes is one entry
    Given the reviewer has selected a PR where classes "RegistrarA", "RegistrarB" and "RegistrarC" were each made final
    When the reviewer opens the PR-level semantic profile
    Then the Structural entries include one "Change Modifiers +final in 3 classes" entry folding 3 changes
