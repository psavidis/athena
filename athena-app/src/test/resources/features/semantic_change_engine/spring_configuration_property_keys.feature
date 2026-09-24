Feature: Spring configuration property keys
  A field added to or removed from a @ConfigurationProperties class is a
  configuration property a user sets in application.properties, so the
  reviewer sees the property key itself (ticket #295; #258 re-run report,
  improvement #6).

  Scenario: A field added to a nested class of a @ConfigurationProperties class names its property key
    Given a @ConfigurationProperties class "KafkaProperties" with prefix "spring.kafka" and a nested class "Listener"
    When field "awaitAsyncResultsOnStop" is added to "KafkaProperties.Listener"
    Then the added field's Framework classification is "Spring: Configuration Property spring.kafka.listener.await-async-results-on-stop"

  Scenario: A field added directly to a @ConfigurationProperties class names its property key
    Given a @ConfigurationProperties class "ServerProperties" with prefix "server" and a nested class "Tomcat"
    When field "maxHttpHeaderSize" is added to "ServerProperties"
    Then the added field's Framework classification is "Spring: Configuration Property server.max-http-header-size"

  Scenario: The prefix may be given as the annotation's prefix attribute
    Given a @ConfigurationProperties class "ServerProperties" with prefix attribute "server" and a nested class "Tomcat"
    When field "maxHttpHeaderSize" is added to "ServerProperties.Tomcat"
    Then the added field's Framework classification is "Spring: Configuration Property server.tomcat.max-http-header-size"

  Scenario: A field removed from a @ConfigurationProperties class names the removed property key
    Given a @ConfigurationProperties class "ServerProperties" with prefix "server" and a nested class "Tomcat"
    When field "legacyMode" is removed from "ServerProperties.Tomcat"
    Then the removed field's Framework classification is "Spring: Removed Configuration Property server.tomcat.legacy-mode"

  Scenario: A field added to an ordinary class is not a configuration property
    Given an ordinary class "KafkaSettings" with a nested class "Listener"
    When field "awaitAsyncResultsOnStop" is added to "KafkaSettings.Listener"
    Then the added field has no Framework classification
