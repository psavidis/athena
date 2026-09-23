Feature: Field type change detection
  A field that keeps its name but changes its declared type is reported
  as one field-type change rather than a removed field plus an added
  field, and says when the field is visible outside its class
  (ticket #267).

  Scenario: A changed field type is detected as one change
    Given a base revision where class "AnnotatedMethod" has a private field "_invoker" of type "MethodHolder"
    And a head revision where "AnnotatedMethod" has a private field "_invoker" of type "MethodHandle"
    When the semantic engine detects transformations between the revisions
    Then a field type change is detected involving "AnnotatedMethod#_invoker" from "MethodHolder" to "MethodHandle"
    And no "REMOVE_FIELD" or "ADD_FIELD" transformation is detected involving "AnnotatedMethod#_invoker"

  Scenario: A changed type on a protected field is flagged as visible outside the class
    Given a base revision where class "BeanPropertyWriter" has a protected field "_accessor" of type "AccessorHolder"
    And a head revision where "BeanPropertyWriter" has a protected field "_accessor" of type "MethodHandle"
    When the semantic engine detects transformations between the revisions
    Then a field type change is detected involving "BeanPropertyWriter#_accessor"
    And the change is described as affecting a protected field

  Scenario: A field whose type and name both changed is not a type change
    Given a base revision where class "Cache" has a field "size" of type "int"
    And a head revision where "Cache" has a field "count" of type "long" and no field "size"
    When the semantic engine detects transformations between the revisions
    Then no field type change is detected involving "Cache#size"

  Scenario: A field whose only change is its annotations is not a type change
    Given a base revision where class "Account" has a field "owner" of type "String" annotated "@Autowired"
    And a head revision where "owner" on "Account" has type "String" and no annotation
    When the semantic engine detects transformations between the revisions
    Then a "CHANGE_FIELD_ANNOTATIONS" transformation is detected involving "Account#owner"
    And no field type change is detected involving "Account#owner"
