Feature: Java source parsing foundation
  The Semantic Change Engine (epic #4) needs to turn Java source code into
  a parsed representation before any symbol modeling or change detection
  can happen. This is that foundational capability.

  Scenario: Parsing a valid Java source file succeeds
    Given a Java source file defining a single class with one method
    When the semantic engine parses the file
    Then the parse succeeds
    And the parsed result reports the class's name
    And the parsed result reports the method's name

  Scenario: Parsing an invalid Java source file fails clearly
    Given a Java source file containing invalid Java syntax
    When the semantic engine parses the file
    Then the parse fails
    And the failure identifies that the source could not be parsed
