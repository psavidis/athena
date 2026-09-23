Feature: Method-body modification detection
  Every method or constructor whose body changed appears as a Change,
  even when no more specific detector can classify the edit, so edits
  inside methods are never invisible (ticket #264).

  Scenario: A method whose body changed is reported as a body modification
    Given a base revision where method "format" on class "Printer" returns "name"
    And a head revision where "format" on "Printer" returns "name.trim()" with the same signature
    When the semantic engine detects transformations between the revisions
    Then a body modification is detected involving "Printer#format" categorised as Unknown
    And it shows the method before and after the edit

  Scenario: A constructor whose body changed is reported as a body modification
    Given a base revision where the constructor of class "Account" assigns "balance" directly
    And a head revision where the constructor of "Account" validates "balance" before assigning it
    When the semantic engine detects transformations between the revisions
    Then a body modification is detected involving the constructor of "Account"

  Scenario: Several edited methods each produce their own body modification
    Given a base and head revision where methods "nextName", "nextString" and "nextLong" on class "Reader" each had their bodies restructured
    When the semantic engine detects transformations between the revisions
    Then a body modification is detected involving each of "Reader#nextName", "Reader#nextString" and "Reader#nextLong"

  Scenario: A behavioral edit is not also reported as a body modification
    Given a base revision where method "isAllowed" on class "Guard" checks "user.isActive()"
    And a head revision where "isAllowed" on "Guard" checks "user.isActive() && user.hasPermission()"
    When the semantic engine detects transformations between the revisions
    Then a behavioral change is detected involving "Guard#isAllowed"
    And no body modification is detected involving "Guard#isAllowed"

  Scenario: A formatting-only edit is not reported as a body modification
    Given a base and head revision where method "format" on class "Printer" differs only in whitespace
    When the semantic engine detects transformations between the revisions
    Then no body modification is detected involving "Printer#format"

  Scenario: A method whose signature changed is not also reported as a body modification
    Given a base revision where class "Greeter" has a method "greet" taking no parameters
    And a head revision where "greet" on "Greeter" takes a "String" parameter and uses it in its body
    When the semantic engine detects transformations between the revisions
    Then a "CHANGE_METHOD_SIGNATURE" transformation is detected involving "Greeter#greet"
    And no body modification is detected involving "Greeter#greet"

  Scenario: An unchanged method is not reported
    Given a base and head revision where method "format" on class "Printer" is identical
    When the semantic engine detects transformations between the revisions
    Then no transformation is detected involving "Printer#format"
