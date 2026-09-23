Feature: Changes inside nested and inner classes
  Members of nested (static) and inner classes are analyzed like those of
  top-level classes, named with their outer class, so changes such as a
  new configuration property on a nested properties class are visible
  (ticket #265).

  Scenario: A field added to a nested class is detected
    Given a base revision where nested class "Listener" inside "KafkaProperties" has no field "awaitAsyncResultsOnStop"
    And a head revision where "KafkaProperties.Listener" has a field "awaitAsyncResultsOnStop"
    When the semantic engine detects transformations between the revisions
    Then an "ADD_FIELD" transformation is detected involving "KafkaProperties.Listener#awaitAsyncResultsOnStop"

  Scenario: A method added to a nested class is detected
    Given a base revision where nested class "Listener" inside "KafkaProperties" has no method "isAwaitAsyncResultsOnStop"
    And a head revision where "KafkaProperties.Listener" has a method "isAwaitAsyncResultsOnStop"
    When the semantic engine detects transformations between the revisions
    Then an "ADD_SYMBOL" transformation is detected involving "KafkaProperties.Listener#isAwaitAsyncResultsOnStop"

  Scenario: A method removed from an inner class is detected
    Given a base revision where inner class "Registrar" inside "WebServerConfiguration" has a method "setBeanFactory"
    And a head revision where "WebServerConfiguration.Registrar" no longer has "setBeanFactory"
    When the semantic engine detects transformations between the revisions
    Then a "REMOVE_SYMBOL" transformation is detected involving "WebServerConfiguration.Registrar#setBeanFactory"

  Scenario: A constructor parameter added in a nested class is detected
    Given a base revision where nested class "Registrar" inside "WebServerConfiguration" gets its bean factory through a setter
    And a head revision where "WebServerConfiguration.Registrar" takes the bean factory as a constructor parameter assigned to a same-named field
    When the semantic engine detects transformations between the revisions
    Then an "ADD_CONSTRUCTOR_PARAMETER" transformation is detected involving "WebServerConfiguration.Registrar#beanFactory"

  Scenario: Same-named nested classes in different outer classes are kept apart
    Given a base revision where both "ReactiveConfiguration" and "ServletConfiguration" have a nested class "Registrar" with a method "register"
    And a head revision where only "ServletConfiguration.Registrar" no longer has "register"
    When the semantic engine detects transformations between the revisions
    Then a "REMOVE_SYMBOL" transformation is detected involving "ServletConfiguration.Registrar#register"
    And no transformation is detected involving "ReactiveConfiguration.Registrar#register"

  Scenario: A nested class added as a whole is detected as a class addition
    Given a base revision where class "KafkaProperties" has no nested class "Retry"
    And a head revision where "KafkaProperties" has a nested class "Retry"
    When the semantic engine detects transformations between the revisions
    Then an "ADD_CLASS" transformation is detected involving "KafkaProperties.Retry"

  Scenario: A member of a class nested two levels deep is detected
    Given a base and head revision where class "Outer.Middle.Inner" gains a method "compute"
    When the semantic engine detects transformations between the revisions
    Then an "ADD_SYMBOL" transformation is detected involving "Outer.Middle.Inner#compute"
