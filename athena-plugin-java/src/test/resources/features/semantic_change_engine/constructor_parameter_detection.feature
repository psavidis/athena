Feature: Constructor parameters stored into a same-named field
  A constructor that gains a parameter and stores it — directly, cast, or
  through an expression using it — into the same-named field is an added
  constructor parameter (tickets #86, #294).

  Scenario: A parameter assigned straight to the same-named field is an added constructor parameter
    Given a base revision where class "Registrar" has a field "beanFactory" and no constructor
    And a head revision where "Registrar" stores constructor parameter "beanFactory" as "this.beanFactory = beanFactory;"
    When the semantic engine detects transformations between the revisions
    Then a "ADD_CONSTRUCTOR_PARAMETER" transformation is detected involving "Registrar#beanFactory"

  Scenario: A parameter assigned through a cast is an added constructor parameter
    Given a base revision where class "Registrar" has a field "beanFactory" and no constructor
    And a head revision where "Registrar" stores constructor parameter "beanFactory" as "this.beanFactory = (ListableBeanFactory) beanFactory;"
    When the semantic engine detects transformations between the revisions
    Then a "ADD_CONSTRUCTOR_PARAMETER" transformation is detected involving "Registrar#beanFactory"

  Scenario: A parameter assigned through a conditional expression is an added constructor parameter
    Given a base revision where class "Registrar" has a field "beanFactory" and no constructor
    And a head revision where "Registrar" stores constructor parameter "beanFactory" as "this.beanFactory = (beanFactory instanceof ListableBeanFactory listable) ? listable : null;"
    When the semantic engine detects transformations between the revisions
    Then a "ADD_CONSTRUCTOR_PARAMETER" transformation is detected involving "Registrar#beanFactory"

  Scenario: A parameter only used, never stored, is not an added constructor parameter
    Given a base revision where class "Registrar" has a field "beanFactory" and no constructor
    And a head revision where "Registrar" stores constructor parameter "beanFactory" as "this.beanFactory = null; beanFactory.toString();"
    When the semantic engine detects transformations between the revisions
    Then no "ADD_CONSTRUCTOR_PARAMETER" transformation is detected involving "Registrar#beanFactory"
