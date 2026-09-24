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

  Scenario: A changed method signature preserves its underlying diff
    Given a base revision where class "Greeter" has a method "greet" taking no parameters
    And a head revision where class "Greeter" has a method "greet" taking a "String" parameter
    When the semantic engine detects transformations between the revisions
    Then the "CHANGE_METHOD_SIGNATURE" transformation involving "Greeter#greet" has a diff showing removed text "greet()" and added text "greet(String arg)"

  Scenario: A renamed class is detected as a class rename
    Given a base revision where class "Greeter" has a method "greet" and a field "prefix"
    And a head revision where the same file instead declares class "Salutation" with the same method and field
    When the semantic engine detects transformations between the revisions
    Then a "RENAME_CLASS" transformation is detected involving "Greeter" and "Salutation"

  Scenario: A renamed class's unchanged members are not separately reported
    Given a base revision where class "Greeter" has a method "greet" and a field "prefix"
    And a head revision where the same file instead declares class "Salutation" with the same method and field
    When the semantic engine detects transformations between the revisions
    Then no structural transformation is detected involving "Greeter#greet"
    And no structural transformation is detected involving "Salutation#greet"

  Scenario: A moved class is detected as a class move
    Given a base revision where class "Greeter" has a method "greet" and a field "prefix" in file "Greeter.java"
    And a head revision where the same class has been moved to file "text/Greeter.java" unchanged
    When the semantic engine detects transformations between the revisions
    Then a "MOVE_CLASS" transformation is detected involving "Greeter"

  Scenario: An added class is detected as a class add
    Given a base revision with no class "Farewell"
    And a head revision where class "Farewell" has a method "sayBye"
    When the semantic engine detects transformations between the revisions
    Then an "ADD_CLASS" transformation is detected involving "Farewell"

  Scenario: A removed class is detected as a class remove
    Given a base revision where class "Obsolete" has a method "run"
    And a head revision with no class "Obsolete"
    When the semantic engine detects transformations between the revisions
    Then a "REMOVE_CLASS" transformation is detected involving "Obsolete"

  Scenario: A renamed field is detected as a field rename
    Given a base revision where class "Account" has a field "balance" of type "int"
    And a head revision where class "Account" has a field "currentBalance" of type "int" instead
    When the semantic engine detects transformations between the revisions
    Then a "RENAME_FIELD" transformation is detected involving "Account#balance" and "Account#currentBalance"

  Scenario: A moved field is detected as a field move
    Given a base revision where class "Account" has a field "balance" of type "int" and class "Wallet" is empty
    And a head revision where class "Wallet" has a field "balance" of type "int" and class "Account" is empty
    When the semantic engine detects transformations between the revisions
    Then a "MOVE_FIELD" transformation is detected involving "Account#balance" and "Wallet#balance"

  Scenario: An added field is detected as a field add
    Given a base revision where class "Account" has no field "balance"
    And a head revision where class "Account" has an additional field "balance" of type "int"
    When the semantic engine detects transformations between the revisions
    Then an "ADD_FIELD" transformation is detected involving "Account#balance"

  Scenario: A removed field is detected as a field remove
    Given a base revision where class "Account" has a field "balance" of type "int"
    And a head revision where class "Account" no longer has the field "balance"
    When the semantic engine detects transformations between the revisions
    Then a "REMOVE_FIELD" transformation is detected involving "Account#balance"

  # Ticket #335: a class moved between packages names both packages.

  Scenario: A class moved between packages under the same name names both packages
    Given a base revision where class "ServiceResource" lives in package "io.vertx.core.impl"
    And a head revision where "ServiceResource" lives unchanged in package "io.vertx.core.internal"
    When the semantic engine detects transformations between the revisions
    Then the change "Move class impl.ServiceResource -> internal.ServiceResource" is detected
