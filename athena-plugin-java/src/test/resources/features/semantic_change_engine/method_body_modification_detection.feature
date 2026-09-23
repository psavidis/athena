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

  # Ticket #288: a body modification says what changed inside the method.

  Scenario: A body edit that adds a call names the call
    Given a base revision where method "exchange" on class "TokenExchange" returns its token
    And a head revision where "exchange" on "TokenExchange" calls "triggerOnEvent" before returning its token
    When the semantic engine detects transformations between the revisions
    Then the body modification of "TokenExchange#exchange" is described as "Modify body of TokenExchange#exchange: +triggerOnEvent"

  Scenario: A body edit that removes a call names the call
    Given a base revision where method "exchange" on class "TokenExchange" calls "audit" before returning its token
    And a head revision where "exchange" on "TokenExchange" returns its token
    When the semantic engine detects transformations between the revisions
    Then the body modification of "TokenExchange#exchange" is described as "Modify body of TokenExchange#exchange: -audit"

  Scenario: A body edit that starts throwing an exception names the exception type
    Given a base revision where method "exchange" on class "TokenExchange" returns its token
    And a head revision where "exchange" on "TokenExchange" throws "IllegalStateException" on failure
    When the semantic engine detects transformations between the revisions
    Then the body modification of "TokenExchange#exchange" is described as "Modify body of TokenExchange#exchange: +throw IllegalStateException"

  Scenario: A body edit that changes how many values are returned says so
    Given a base revision where method "exchange" on class "TokenExchange" returns its token
    And a head revision where "exchange" on "TokenExchange" returns a fallback token on failure
    When the semantic engine detects transformations between the revisions
    Then the body modification of "TokenExchange#exchange" is described as "Modify body of TokenExchange#exchange: return statements 1 → 2"

  Scenario: A long summary is cut after five items
    Given a base revision where method "exchange" on class "TokenExchange" returns its token
    And a head revision where "exchange" on "TokenExchange" calls "a", "b", "c", "d", "e", "f" and "g" before returning its token
    When the semantic engine detects transformations between the revisions
    Then the body modification of "TokenExchange#exchange" is described as "Modify body of TokenExchange#exchange: +a, +b, +c, +d, +e …and 2 more"

  Scenario: A body edit with no call, throw or return difference reads as other statements
    Given a base revision where method "combine" on class "Calculator" returns "a + b"
    And a head revision where "combine" on "Calculator" returns "a - b" with the same signature
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Calculator#combine" is described as "Modify body of Calculator#combine: other statements changed"

  Scenario: Changed arguments to an existing call are not a call added or removed
    Given a base revision where method "exchange" on class "TokenExchange" calls "audit" before returning its token
    And a head revision where "exchange" on "TokenExchange" calls "audit" with a different argument before returning its token
    When the semantic engine detects transformations between the revisions
    Then the body modification of "TokenExchange#exchange" is described as "Modify body of TokenExchange#exchange: other statements changed"
