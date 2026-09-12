Feature: Structural and mechanical change detection
  The Semantic Change Engine analyzes a base and head revision of a Java
  codebase and identifies deterministic structural transformations, so a
  reviewer sees one meaningful transformation instead of raw diff hunks.

  Scenario: A renamed method is detected as a rename
    Given a base revision where class "Greeter" has a method "greet"
    And a head revision where class "Greeter" has a method "salute" with the same body
    When the semantic engine detects transformations between the revisions
    Then a "RENAME_SYMBOL" transformation is detected involving "Greeter#greet" and "Greeter#salute"

  Scenario: A moved method is detected as a move
    Given a base revision where class "Greeter" has a method "greet" and class "Farewell" is empty
    And a head revision where class "Farewell" has a method "greet" with the same body and class "Greeter" is empty
    When the semantic engine detects transformations between the revisions
    Then a "MOVE_SYMBOL" transformation is detected involving "Greeter#greet" and "Farewell#greet"

  Scenario: An added method is detected as an add
    Given a base revision where class "Greeter" has no method "farewell"
    And a head revision where class "Greeter" has an additional method "farewell"
    When the semantic engine detects transformations between the revisions
    Then an "ADD_SYMBOL" transformation is detected involving "Greeter#farewell"

  Scenario: A removed method is detected as a remove
    Given a base revision where class "Greeter" has a method "obsolete"
    And a head revision where class "Greeter" no longer has the method "obsolete"
    When the semantic engine detects transformations between the revisions
    Then a "REMOVE_SYMBOL" transformation is detected involving "Greeter#obsolete"

  Scenario: A changed method signature is detected
    Given a base revision where class "Greeter" has a method "greet" taking no parameters
    And a head revision where class "Greeter" has a method "greet" taking a "String" parameter
    When the semantic engine detects transformations between the revisions
    Then a "CHANGE_METHOD_SIGNATURE" transformation is detected involving "Greeter#greet"

  Scenario: A changed record component list is detected as a signature change
    Given a base revision where record "Money" has components "int amount"
    And a head revision where record "Money" has components "int amount, String currency"
    When the semantic engine detects transformations between the revisions
    Then a "CHANGE_METHOD_SIGNATURE" transformation is detected involving "Money"

  Scenario: An extracted method is detected
    Given a base revision where class "Greeter" has a method "greet" with an inline fragment
    And a head revision where that fragment has been extracted into a new method "buildGreeting" called from "greet"
    When the semantic engine detects transformations between the revisions
    Then an "EXTRACT_METHOD" transformation is detected involving "Greeter#greet" and "Greeter#buildGreeting"

  Scenario: A mechanical identifier replacement applied many times is detected
    Given a base revision where 3 files each reference the identifier "Foo"
    And a head revision where every occurrence of "Foo" has been replaced with "Bar"
    When the semantic engine detects transformations between the revisions
    Then a "MECHANICAL_REPLACEMENT" transformation is detected with 3 occurrences

  Scenario: A formatting-only change is detected
    Given a base revision where class "Greeter" has a method "greet"
    And a head revision where the same method is reformatted with different whitespace but identical structure
    When the semantic engine detects transformations between the revisions
    Then a "FORMATTING_ONLY" transformation is detected involving "Greeter#greet"

  Scenario: A changed method call is not flagged as any structural transformation
    Given a base revision where class "Greeter" has a method "greet" that calls "formatA()"
    And a head revision where "greet" instead calls "formatB()" with no other structural change
    When the semantic engine detects transformations between the revisions
    Then no structural transformation is detected involving "Greeter#greet"
