Feature: The same change repeated across test classes is shown once
  A wide test-only refactoring — the same member removed from many test
  classes — reads as one Explorer card with its count, not one "Test changes
  in X" card per class, just as #291 folds repeated production changes
  (ticket #363; third expansion baseline, K6).

  Scenario: The same method removed from several test classes is one entry
    Given the reviewer has selected a PR where method "afterEachTest" was removed from test classes "FirstTest", "SecondTest" and "ThirdTest"
    When the reviewer opens the PR-level semantic profile
    Then the Structural entries include one "Remove afterEachTest in 3 test classes" entry folding 3 changes
    And that entry names the classes "FirstTest, SecondTest, ThirdTest"
    And no Structural entry mentions "Test changes in"

  Scenario: A test change that doesn't repeat stays in its test class's entry
    Given the reviewer has selected a PR where method "afterEachTest" was removed from test classes "FirstTest", "SecondTest" and "ThirdTest", and method "setUp" from "FirstTest" only
    When the reviewer opens the PR-level semantic profile
    Then the Structural entries include one "Remove afterEachTest in 3 test classes" entry folding 3 changes
    And the Structural entries include one "Test changes in FirstTest" entry folding 1 change
    And no Structural entry mentions "Test changes in SecondTest"

  Scenario: A change repeated once in production and once in a test class is not folded
    Given the reviewer has selected a PR where method "afterEachTest" was removed from production class "Registrar" and test class "FirstTest"
    When the reviewer opens the PR-level semantic profile
    Then no Structural entry mentions "afterEachTest in"

  Scenario: The module-level profile folds repeated test changes the same way
    Given the reviewer has selected a PR where method "afterEachTest" was removed from test classes "FirstTest", "SecondTest" and "ThirdTest"
    When the reviewer opens the semantic profile of module "core"
    Then the Structural entries include one "Remove afterEachTest in 3 test classes" entry folding 3 changes

  Scenario: The Change Map still lists every test change
    Given the reviewer has selected a PR where method "afterEachTest" was removed from test classes "FirstTest", "SecondTest" and "ThirdTest"
    When the reviewer views the Change Map of that PR
    Then the Change Map lists 3 Changes

  # Ticket #374: a fold is shown only when it takes over a whole test class's card,
  # so folding never makes the Explorer longer.

  Scenario: The same new test in two test classes that change otherwise too is not folded
    Given the reviewer has selected a PR where test "testRejoin" was added to test classes "FirstTest" and "SecondTest", each also gaining a test of its own
    When the reviewer opens the PR-level semantic profile
    Then no Structural entry mentions "testRejoin in"
    And the Structural entries include one "Test changes in FirstTest" entry folding 2 changes
    And the Structural entries include one "Test changes in SecondTest" entry folding 2 changes

  Scenario: A fold that takes over one test class's only change is kept
    Given the reviewer has selected a PR where test "testRejoin" was added to test classes "FirstTest" and "SecondTest", and "SecondTest" also gains a test of its own
    When the reviewer opens the PR-level semantic profile
    Then the Structural entries include one "Add testRejoin in 2 test classes" entry folding 2 changes
    And the Structural entries include one "Test changes in SecondTest" entry folding 1 change
    And no Structural entry mentions "Test changes in FirstTest"
