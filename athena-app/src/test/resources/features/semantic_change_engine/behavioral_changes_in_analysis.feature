Feature: Behavioral Changes appear in the analysis
  Condition and control-flow edits inside an existing method are reported
  as Behavioral Changes in the analysis a reviewer sees, the highest-
  priority category in the Change Map (ticket #263, completing #18).

  Scenario: A changed condition appears as a Behavioral Change
    Given a base revision where method "isAllowed" on class "Guard" checks "user.isActive()"
    And a head revision where "isAllowed" on "Guard" checks "user.isActive() && user.hasPermission()"
    When the engine analyzes the revision pair
    Then the analysis reports a Behavioral Change on "Guard#isAllowed" described as a changed condition
    And that Change shows the method before and after the edit

  Scenario: An added branch appears as a Behavioral Change
    Given a base revision where method "markAsRemoved" on class "Cache" always decrements the size
    And a head revision where "markAsRemoved" on "Cache" first returns early when the entry is already removed
    When the engine analyzes the revision pair
    Then the analysis reports a Behavioral Change on "Cache#markAsRemoved" described as an added branch

  Scenario: A loop replacing a condition appears as a Behavioral Change
    Given a base revision where method "reduce" on class "Fraction" halves both values once under an if
    And a head revision where "reduce" on "Fraction" halves both values in a while loop
    When the engine analyzes the revision pair
    Then the analysis reports a Behavioral Change on "Fraction#reduce"

  Scenario: The Change Map counts Behavioral Changes
    Given a revision pair with one changed condition and one renamed method
    When the reviewer views the Change Map for it
    Then the summary shows 1 Behavioral and 1 Structural Change

  Scenario: A changed method call alone is not a Behavioral Change
    Given a base revision where method "greet" on class "Greeter" calls "formatA()"
    And a head revision where "greet" on "Greeter" instead calls "formatB()" with no condition or branch change
    When the engine analyzes the revision pair
    Then the analysis reports no Behavioral Change on "Greeter#greet"
