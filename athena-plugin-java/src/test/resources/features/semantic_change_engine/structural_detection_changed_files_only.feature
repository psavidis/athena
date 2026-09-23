Feature: Structural detection only analyzes files that changed
  Structural detection works from the files that differ between the two
  revisions, so its cost follows the size of the change and no detected
  transformation cites an untouched file (ticket #271).

  Scenario: A method moved between two changed files is still detected
    Given a base revision where class "Greeter" has a method "greet" and class "Farewell" is empty
    And a head revision where class "Farewell" has a method "greet" with the same body and class "Greeter" is empty
    And the repository contains many other unchanged classes
    When the semantic engine detects transformations between the revisions
    Then a "MOVE_SYMBOL" transformation is detected involving "Greeter#greet" and "Farewell#greet"

  Scenario: A class renamed between revisions is still detected
    Given a base revision with class "UserService" and a head revision where it is renamed "AccountService" with the same members
    When the semantic engine detects transformations between the revisions
    Then a "RENAME_CLASS" transformation is detected involving "UserService" and "AccountService"

  Scenario: A method whose body matches one in an unchanged file is not reported as moved
    Given a base and head revision where unchanged class "Legacy" has a method "compute" with a given body
    And the head revision adds class "Modern" with a method "compute" with the same body
    When the semantic engine detects transformations between the revisions
    Then an "ADD_SYMBOL" transformation is detected involving "Modern#compute"
    And no transformation cites a file of class "Legacy"

  Scenario: An unparseable unchanged file does not affect detection
    Given a base and head revision where an unchanged file fails to parse
    And class "Greeter" gains a method "farewell" in a changed file
    When the semantic engine detects transformations between the revisions
    Then an "ADD_SYMBOL" transformation is detected involving "Greeter#farewell"
