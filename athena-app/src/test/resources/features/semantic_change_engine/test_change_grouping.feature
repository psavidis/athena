Feature: Test changes are grouped per test class in the Explorer
  Production changes must not be buried under test fixtures: in the PR- and
  module-level Semantic Change Explorer, every test-code Change of one test
  class is folded into a single Structural entry (ticket #287; #258 re-run
  report, N1).

  Scenario: A test class's structural changes are folded into one entry
    Given the reviewer has selected a PR where 3 methods were added to test class "GreeterTest" and 1 to production class "Greeter"
    When the reviewer opens the PR-level semantic profile
    Then the Structural entries include one "Test changes in GreeterTest" entry folding 3 changes
    And that entry lists the file "core/src/test/java/com/acme/GreeterTest.java"
    And no other Structural entry is a change in "GreeterTest"

  Scenario: Changes in a nested class of a test class count toward the test class
    Given the reviewer has selected a PR where methods were added to test class "GreeterTest" and to its nested class "Fixtures"
    When the reviewer opens the PR-level semantic profile
    Then the Structural entries include one "Test changes in GreeterTest" entry folding 2 changes

  Scenario: Each test class gets its own group
    Given the reviewer has selected a PR where methods were added to test classes "GreeterTest" and "PricingTest"
    When the reviewer opens the PR-level semantic profile
    Then the Structural entries include one "Test changes in GreeterTest" entry folding 1 change
    And the Structural entries include one "Test changes in PricingTest" entry folding 1 change

  Scenario: Production entries are listed before test groups
    Given the reviewer has selected a PR where 3 methods were added to test class "GreeterTest" and 1 to production class "Greeter"
    When the reviewer opens the PR-level semantic profile
    Then every production Structural entry comes before every test group

  Scenario: The module-level profile groups test changes the same way
    Given the reviewer has selected a PR where 3 methods were added to test class "GreeterTest" and 1 to production class "Greeter"
    When the reviewer opens the semantic profile of module "core"
    Then the Structural entries include one "Test changes in GreeterTest" entry folding 3 changes

  Scenario: The Change Map still counts every test change
    Given the reviewer has selected a PR where 3 methods were added to test class "GreeterTest" and 1 to production class "Greeter"
    When the reviewer views the Change Map of that PR
    Then the Change Map lists 4 Changes
