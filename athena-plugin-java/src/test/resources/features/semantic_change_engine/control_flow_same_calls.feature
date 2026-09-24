Feature: A control-flow change says when the body makes the same calls
  A restructured loop or branch whose body still calls the same methods,
  throws the same exceptions and returns as often says "same calls", so a
  reviewer can tell a restructuring apart from a change in what the method
  calls. It stays behavioral and claims no more than that: a changed condition
  with the same calls can still be a real bug fix (ticket #319; expansion
  baseline, F6).

  Scenario: An unrolled loop makes the same calls
    Given a base revision where method "serializeAll" on class "Serializer" writes each property in a simple loop
    And a head revision where "serializeAll" on "Serializer" writes the properties in a loop unrolled by two
    When the semantic engine detects transformations between the revisions
    Then the control-flow change of "Serializer#serializeAll" ends with "; same calls"
    And it is still categorised as Behavioral

  Scenario: A control-flow change that also calls something new does not say so
    Given a base revision where method "serializeAll" on class "Serializer" writes each property in a simple loop
    And a head revision where "serializeAll" on "Serializer" also logs each skipped property in the loop
    When the semantic engine detects transformations between the revisions
    Then the control-flow change of "Serializer#serializeAll" does not mention "same calls"

  Scenario: A new guard that throws does not say so
    Given a base revision where method "serializeAll" on class "Serializer" writes each property in a simple loop
    And a head revision where "serializeAll" on "Serializer" first rejects an empty property list by throwing
    When the semantic engine detects transformations between the revisions
    Then the control-flow change of "Serializer#serializeAll" does not mention "same calls"

  # Ticket #334: throwing more often is not the same calls.

  Scenario: A new guard that throws a type the method already throws does not say so
    Given a base revision where method "getInvoker" on class "Protocol" throws "RemotingException" once
    And a head revision where "getInvoker" on "Protocol" also throws "RemotingException" from a new guard
    When the semantic engine detects transformations between the revisions
    Then the control-flow change of "Protocol#getInvoker" does not mention "same calls"
