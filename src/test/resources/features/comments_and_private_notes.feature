Feature: Comments and private notes at line, symbol, Change, and review scope
  A reviewer attaches comments and private notes at the scope that matches
  their thinking — a single line, a symbol, a whole Change, or the review
  overall — rather than being forced onto arbitrary lines. Private notes
  are visually distinguishable from comments (epic #5 §21, §22).

  Scenario: A reviewer adds a comment at line scope
    Given a reviewer viewing line 12 of file "Greeter.java"
    When the reviewer adds the comment "Why return null here?" at that line
    Then the line's comments include "Why return null here?"

  Scenario: A reviewer adds a comment at symbol scope
    Given a reviewer viewing symbol "Greeter#greet"
    When the reviewer adds the comment "This method needs a Javadoc" at that symbol
    Then the symbol's comments include "This method needs a Javadoc"

  Scenario: A reviewer adds a comment at Change scope
    Given a reviewer viewing a Change
    When the reviewer adds the comment "Good refactor" at that Change
    Then the Change's comments include "Good refactor"

  Scenario: A reviewer adds a comment at review scope
    Given a reviewer viewing the whole review
    When the reviewer adds the comment "Overall looks solid" at review scope
    Then the review's comments include "Overall looks solid"

  Scenario: A reviewer adds a private note that is visually distinct from a comment
    Given a reviewer viewing a Change
    When the reviewer adds the private note "Not sure this is safe, ask in standup" at that Change
    Then the Change's private notes include "Not sure this is safe, ask in standup"
    And the Change's comments do not include "Not sure this is safe, ask in standup"

  Scenario: Comments and private notes at the same scope are listed separately
    Given a reviewer viewing a Change
    When the reviewer adds the comment "Nice cleanup" at that Change
    And the reviewer adds the private note "Double-check with the author" at that Change
    Then the Change's comments include "Nice cleanup"
    And the Change's private notes include "Double-check with the author"
    And the Change's comments do not include "Double-check with the author"
