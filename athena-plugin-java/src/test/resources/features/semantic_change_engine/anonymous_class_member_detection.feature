Feature: Members added to or removed from anonymous classes
  A field or method added to (or removed from) an anonymous class that exists in
  both revisions is reported on its own, named after the member that creates
  the anonymous class, so cached state added inside an anonymous class is
  visible (ticket #320; expansion baseline, F7).

  Scenario: A field added to an anonymous class is reported
    Given a base revision where method "createBoundField" on class "ReflectiveFactory" returns an anonymous "BoundField" with no fields
    And a head revision where that anonymous "BoundField" in "ReflectiveFactory#createBoundField" gains field "knownAccessible"
    When the semantic engine detects transformations between the revisions
    Then the change "Add field ReflectiveFactory#createBoundField(anonymous BoundField)#knownAccessible" is detected

  Scenario: A method added to an anonymous class is reported
    Given a base revision where method "createBoundField" on class "ReflectiveFactory" returns an anonymous "BoundField" with no fields
    And a head revision where that anonymous "BoundField" in "ReflectiveFactory#createBoundField" gains method "reset"
    When the semantic engine detects transformations between the revisions
    Then the change "Add ReflectiveFactory#createBoundField(anonymous BoundField)#reset" is detected

  Scenario: A field removed from an anonymous class is reported
    Given a base revision where the anonymous "BoundField" in method "createBoundField" on class "ReflectiveFactory" has field "cache"
    And a head revision where that anonymous "BoundField" in "ReflectiveFactory#createBoundField" has no fields
    When the semantic engine detects transformations between the revisions
    Then the change "Remove field ReflectiveFactory#createBoundField(anonymous BoundField)#cache" is detected

  Scenario: The members of a newly created anonymous class are not reported one by one
    Given a base revision where method "createBoundField" on class "ReflectiveFactory" returns null
    And a head revision where "createBoundField" on "ReflectiveFactory" returns an anonymous "BoundField" with field "knownAccessible"
    When the semantic engine detects transformations between the revisions
    Then no transformation is detected involving "(anonymous BoundField)"
