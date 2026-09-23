Feature: Parameter and return annotation change detection
  Added or removed annotations on a method's parameters or return type,
  such as a nullness annotation, are reported as a change to the method's
  contract, so an API contract change is never an empty review
  (ticket #268).

  Scenario: An annotation added to a method parameter is detected
    Given a base revision where method "when" on class "Mockito" takes parameter "methodCall" with no annotation
    And a head revision where parameter "methodCall" of "Mockito#when" is annotated "@Nullable"
    When the semantic engine detects transformations between the revisions
    Then a parameter annotation change is detected involving "Mockito#when"
    And it names parameter "methodCall" as having gained "@Nullable"

  Scenario: An annotation removed from a method parameter is detected
    Given a base revision where method "save" on class "Repository" takes parameter "entity" annotated "@NonNull"
    And a head revision where parameter "entity" of "Repository#save" has no annotation
    When the semantic engine detects transformations between the revisions
    Then a parameter annotation change is detected involving "Repository#save"
    And it names parameter "entity" as having lost "@NonNull"

  Scenario: Annotations added to several parameters are reported together
    Given a base revision where method "thenReturn" on interface "OngoingStubbing" takes parameters "value" and "values" with no annotations
    And a head revision where both parameters of "OngoingStubbing#thenReturn" are annotated "@Nullable"
    When the semantic engine detects transformations between the revisions
    Then one parameter annotation change is detected involving "OngoingStubbing#thenReturn" naming both parameters

  Scenario: An annotation added to a constructor parameter is detected
    Given a base revision where the constructor of class "Account" takes parameter "owner" with no annotation
    And a head revision where parameter "owner" of the "Account" constructor is annotated "@Nullable"
    When the semantic engine detects transformations between the revisions
    Then a parameter annotation change is detected involving the constructor of "Account"

  Scenario: An annotation added to a method's return type is detected
    Given a base revision where method "find" on class "Repository" has no annotation on its return type
    And a head revision where "Repository#find" is annotated "@Nullable" on its return type
    When the semantic engine detects transformations between the revisions
    Then a return annotation change is detected involving "Repository#find"

  Scenario: A parameter annotation change is not reported as a signature change
    Given a base and head revision where the only difference in method "when" on class "Mockito" is "@Nullable" on its parameter
    When the semantic engine detects transformations between the revisions
    Then no "CHANGE_METHOD_SIGNATURE" transformation is detected involving "Mockito#when"
