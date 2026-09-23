Feature: Changes in test code are recognized as test code
  A reviewer needs to tell test changes apart from production changes, and
  a new test method must never be presented as a new business capability
  (ticket #285; #258 evaluation, R7).

  Scenario Outline: A Change in a test source is marked as test code
    Given the reviewer has selected a PR where a method was added to the class in "<file>"
    When the reviewer views the Change Map of that PR
    Then the Change for that method is marked as test code

    Examples:
      | file                                        |
      | core/src/test/java/com/acme/Greeter.java    |
      | core/src/it/java/com/acme/Greeter.java      |
      | core/src/testFixtures/java/com/acme/Greeter.java |
      | test/com/acme/Greeter.java                  |
      | tests/com/acme/Greeter.java                 |
      | testsuite/com/acme/Greeter.java             |
      | core/src/main/java/com/acme/GreeterTest.java  |
      | core/src/main/java/com/acme/GreeterTests.java |
      | core/src/main/java/com/acme/GreeterIT.java    |
      | core/src/main/java/com/acme/GreeterTestCase.java |

  Scenario Outline: A Change in production code is not marked as test code
    Given the reviewer has selected a PR where a method was added to the class in "<file>"
    When the reviewer views the Change Map of that PR
    Then the Change for that method is not marked as test code

    Examples:
      | file                                         |
      | core/src/main/java/com/acme/Greeter.java     |
      | core/src/main/java/com/acme/TestDataLoader.java |
      | core/src/main/java/com/acme/testing/Greeter.java |

  Scenario: The Change detail says whether a Change is test code
    Given the reviewer has selected a PR where a method was added to the class in "core/src/test/java/com/acme/Greeter.java"
    When the reviewer opens the detail of the Change for that method
    Then the detail marks the Change as test code

  Scenario: The Semantic Canvas marks a territory's test-code Changes
    Given the reviewer has selected a PR where one method was added in production code and one in test code of the same module
    When the reviewer opens the Semantic Canvas topology
    Then the module's territory lists both Changes
    And only the test-code Change is marked as test code

  Scenario: A method added in test code is not presented as a new capability
    Given the reviewer has selected a PR where a method was added to the class in "core/src/test/java/com/acme/GreeterTest.java"
    When the reviewer opens the Semantic Profile of the Change for that method
    Then the profile has no Responsibility classification
    And the profile still has a Structural classification

  Scenario: A method added in production code is still presented as a new capability
    Given the reviewer has selected a PR where a method was added to the class in "core/src/main/java/com/acme/Greeter.java"
    When the reviewer opens the Semantic Profile of the Change for that method
    Then the profile's Responsibility classification is "Add Capability"
