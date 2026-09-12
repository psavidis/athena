Feature: Narrow behavioral (condition/control-flow) change detection
  The Semantic Change Engine flags condition and control-flow changes as
  behavioral, since they may alter runtime behavior even when the textual
  diff looks small — this is the narrow, MVP-scoped slice of "basic
  behavioral modifications" from epic #4.

  Scenario: A changed if-condition is detected as behavioral
    Given a base revision where method "isAllowed" on class "Guard" has condition "user.isActive()"
    And a head revision where "isAllowed" on "Guard" has condition "user.isActive() && user.hasPermission()"
    When the semantic engine detects behavioral changes between the revisions
    Then a behavioral change is detected involving "Guard#isAllowed"

  Scenario: An added branch is detected as behavioral
    Given a base revision where method "classify" on class "Grader" has only an if-branch
    And a head revision where "classify" on "Grader" has an additional else-branch
    When the semantic engine detects behavioral changes between the revisions
    Then a behavioral change is detected involving "Grader#classify"

  Scenario: An added loop is detected as behavioral
    Given a base revision where method "process" on class "Batch" has no loop
    And a head revision where "process" on "Batch" has an added loop over the input
    When the semantic engine detects behavioral changes between the revisions
    Then a behavioral change is detected involving "Batch#process"

  Scenario: A changed method call is not detected as behavioral
    Given a base revision where method "greet" on class "Greeter" calls "formatA()"
    And a head revision where "greet" on "Greeter" instead calls "formatB()" with no condition or branch change
    When the semantic engine detects behavioral changes between the revisions
    Then no behavioral change is detected involving "Greeter#greet"

  Scenario: A changed return expression is not detected as behavioral
    Given a base revision where method "value" on class "Holder" returns "a"
    And a head revision where "value" on "Holder" returns "b" with no condition or branch change
    When the semantic engine detects behavioral changes between the revisions
    Then no behavioral change is detected involving "Holder#value"
