Feature: Spring Aware callback to constructor injection
  Replacing a Spring *Aware setter callback (setBeanFactory, setBeanClassLoader,
  setEnvironment, setResourceLoader) with a constructor that receives the same
  dependency is one framework-level migration, recognized as such (ticket #294;
  #258 re-run report, improvement #6).

  Scenario: A BeanFactoryAware setter replaced by a constructor parameter is recognized
    Given a class "ImportsRegistrar" whose "setBeanFactory" callback is replaced by a constructor receiving a "BeanFactory"
    When Athena analyzes the change
    Then the constructor-parameter Change of "ImportsRegistrar" has the Framework classification "Spring: Aware Callback to Constructor Injection"
    And that classification's evidence shows the removed setter before the added constructor parameter

  Scenario: A constructor that casts the received dependency is recognized
    Given a class "ImportsSelector" whose "setBeanFactory" callback is replaced by a constructor casting the received "BeanFactory"
    When Athena analyzes the change
    Then the constructor-parameter Change of "ImportsSelector" has the Framework classification "Spring: Aware Callback to Constructor Injection"

  Scenario: A BeanClassLoaderAware setter replaced by a constructor parameter is recognized
    Given a class "ConfigurationSelector" whose "setBeanClassLoader" callback is replaced by a constructor receiving a "ClassLoader"
    When Athena analyzes the change
    Then the constructor-parameter Change of "ConfigurationSelector" has the Framework classification "Spring: Aware Callback to Constructor Injection"

  Scenario: A removed setter without a constructor receiving the dependency is not recognized
    Given a class "ImportsRegistrar" whose "setBeanFactory" callback is removed with nothing replacing it
    When Athena analyzes the change
    Then no Change of "ImportsRegistrar" has a Framework classification

  Scenario: A constructor receiving a different type is not recognized
    Given a class "ImportsRegistrar" whose "setBeanFactory" callback is replaced by a constructor receiving an "Environment"
    When Athena analyzes the change
    Then no Change of "ImportsRegistrar" has a Framework classification
