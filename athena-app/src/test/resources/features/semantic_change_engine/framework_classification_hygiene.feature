Feature: Framework classification recognizes whole annotations in production code
  A framework classification must name a real framework mechanism: an
  annotation is matched as a whole token, and test code — which declares
  entities and registries for its own sake — isn't classified as using a
  framework, except for the test framework itself (ticket #315; expansion
  baseline, F3).

  Scenario: An annotation that only starts like a Spring stereotype is not that stereotype
    Given a production class "RestrictedToOneFixture" is added annotated "@ServiceRegistry"
    When Athena classifies the changes
    Then the class's Change has no Framework classification

  Scenario: A Spring stereotype in production code is still recognized
    Given a production class "OrderService" is added annotated "@Service"
    When Athena classifies the changes
    Then the class's Change has the Framework classification "Spring: @Service"

  Scenario: A fully qualified Spring stereotype is recognized
    Given a production class "OrderService" is added annotated "@org.springframework.stereotype.Service"
    When Athena classifies the changes
    Then the class's Change has the Framework classification "Spring: @Service"

  Scenario: A JPA relationship declared in test code is not classified
    Given a test class "LibraryTest" whose nested entity gains a field annotated "@OneToMany"
    When Athena classifies the changes
    Then no Change has a Framework classification

  Scenario: A JUnit lifecycle annotation in test code is still classified
    Given a test class "LibraryTest" gaining a method annotated "@BeforeEach"
    When Athena classifies the changes
    Then the method's Change has the Framework classification "JUnit: Lifecycle/Extensions"
