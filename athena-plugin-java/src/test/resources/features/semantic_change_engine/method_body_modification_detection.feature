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
    Given a base revision where method "combine" on class "Calculator" stores "a + b" before returning it
    And a head revision where "combine" on "Calculator" stores "a - b" before returning it
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Calculator#combine" is described as "Modify body of Calculator#combine: other statements changed"

  Scenario: Changed arguments to an existing call are not a call added or removed
    Given a base revision where method "exchange" on class "TokenExchange" calls "audit" before returning its token
    And a head revision where "exchange" on "TokenExchange" calls "audit" with a different argument before returning its token
    When the semantic engine detects transformations between the revisions
    Then the body modification of "TokenExchange#exchange" is described as "Modify body of TokenExchange#exchange: other statements changed"

  # Ticket #339: reordered statements and changed return values are named.

  Scenario: A statement moved below another is named
    Given a base revision where method "add" on class "TreeList" increments "modCount" before checking the index
    And a head revision where "add" on "TreeList" checks the index before incrementing "modCount"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "TreeList#add" is described as "Modify body of TreeList#add: moved modCount++ after checkInterval(index, 0, size())"

  Scenario: A changed part of a returned expression is named
    Given a base revision where static method "substringAfter" on class "Strings" returns "index == NOT_FOUND ? str : str.substring(index + 1)"
    And a head revision where static method "substringAfter" on "Strings" returns "index == NOT_FOUND ? EMPTY : str.substring(index + 1)"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Strings#substringAfter" is described as "Modify body of Strings#substringAfter: return str -> EMPTY"

  # Ticket #351: a return summary names only the method's own return, and only whole expressions.

  Scenario: A return inside a lambda in the body is not the method's return
    Given a base revision where static method "firstName" on class "Names" defines a supplier returning "first" and returns "str"
    And a head revision where static method "firstName" on "Names" defines a supplier returning "last" and returns "str"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Names#firstName" is described as "Modify body of Names#firstName: other statements changed"

  Scenario: A returned anonymous class that changed reads as a changed return value
    Given a base revision where static method "view" on class "Sets" returns "new Object() { public String toString() { return str; } }"
    And a head revision where static method "view" on "Sets" returns "new Object() { public String toString() { return str.trim(); } }"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Sets#view" is described as "Modify body of Sets#view: +str.trim, return value changed"

  Scenario: A changed lambda in the returned expression reads as a changed return value
    Given a base revision where static method "present" on class "Strings" returns "Stream.of(str).filter(x -> x != null).findFirst().get()"
    And a head revision where static method "present" on "Strings" returns "Stream.of(str).filter(Objects::nonNull).findFirst().get()"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Strings#present" is described as "Modify body of Strings#present: return value changed"

  Scenario: A difference that isn't a whole expression reads as a changed return value
    Given a base revision where static method "actualPort" on class "Server" returns "server != null ? server.actualPort : actualPort"
    And a head revision where static method "actualPort" on "Server" returns "ref == null ? 0 : ref.get().actualPort"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Server#actualPort" is described as "Modify body of Server#actualPort: +ref.get, return value changed"

  Scenario: A return wrapped in new code doesn't read as returning nothing before
    Given a base revision where static method "sniEntrySize" on class "Server" returns "sniEntrySize()"
    And a head revision where static method "sniEntrySize" on "Server" returns "ref == null ? 0 : ref.get().sniEntrySize()"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Server#sniEntrySize" is described as "Modify body of Server#sniEntrySize: +ref.get, return value changed"

  Scenario: A whole changed expression is still named
    Given a base revision where static method "matcher" on class "RequestCache" returns "str.isEmpty() ? str : fallback"
    And a head revision where static method "matcher" on "RequestCache" returns "str.isEmpty() ? str : defaultValue(index)"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "RequestCache#matcher" is described as "Modify body of RequestCache#matcher: +defaultValue, return fallback -> defaultValue(index)"

  # Ticket #352: an item that repeats is listed once, with a count.

  Scenario: The same changed return value on two paths is listed once with a count
    Given a base revision where static method "matcher" on class "RequestCache" returns "str.isEmpty() ? str : fallback" on both paths
    And a head revision where static method "matcher" on "RequestCache" returns "str.isEmpty() ? str : defaultValue(index)" on both paths
    When the semantic engine detects transformations between the revisions
    Then the body modification of "RequestCache#matcher" is described as "Modify body of RequestCache#matcher: +defaultValue, return fallback -> defaultValue(index) ×2"

  Scenario: Two returns that both changed beyond summary read as one counted item
    Given a base revision where static method "present" on class "Strings" returns "Stream.of(str).filter(x -> x != null).findFirst().get()" on both paths
    And a head revision where static method "present" on "Strings" returns "Stream.of(str).filter(Objects::nonNull).findFirst().get()" on both paths
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Strings#present" is described as "Modify body of Strings#present: return value changed ×2"

  Scenario: Different changed return values are each listed
    Given a base revision where static method "pick" on class "Strings" returns "first" on one path and "str" on the other
    And a head revision where static method "pick" on "Strings" returns "first.trim()" on one path and "str.strip()" on the other
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Strings#pick" is described as "Modify body of Strings#pick: +first.trim, +str.strip, return first -> first.trim(), return str -> str.strip()"
