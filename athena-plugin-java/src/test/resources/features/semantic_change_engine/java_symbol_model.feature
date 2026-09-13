Feature: Java symbol model
  Downstream Semantic Change Engine capabilities (detection, grouping,
  identity) need to reason about classes, methods, and fields as stable
  entities, not raw text. This is the symbol model that gives them that.

  Scenario: Building a symbol model of a small source tree lists its symbols
    Given a Java source tree containing a class "Greeter" with a field "name" and a method "greet"
    When the semantic engine builds a symbol model of the source tree
    Then the symbol model lists a type symbol for "Greeter"
    And the symbol model lists a field symbol for "name" on "Greeter"
    And the symbol model lists a method symbol for "greet" on "Greeter"

  Scenario: Looking up a symbol by its identifier returns its details
    Given a Java source tree containing a class "Greeter" with a method "greet"
    And the semantic engine has built a symbol model of the source tree
    When the engine looks up the "greet" method symbol on "Greeter" by its identifier
    Then the returned symbol identifies "greet" as a method of "Greeter"

  Scenario: The same symbol gets the same identifier across two model builds
    Given a Java source tree containing a class "Greeter" with a method "greet"
    When the semantic engine builds a symbol model of the source tree twice
    Then the "greet" method symbol has the same identifier in both models

  Scenario: A symbol referencing an unresolvable external type does not abort the whole model
    Given a Java source tree containing a class "Greeter" whose method body references an external library type not on the classpath
    When the semantic engine builds a symbol model of the source tree
    Then the symbol model still lists a type symbol for "Greeter"
    And the symbol model reports a resolution failure for the unresolvable reference
