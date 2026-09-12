Feature: Project wiring smoke test
  A minimal scenario proving the Maven + Cucumber-JVM + JUnit 5 test
  pipeline is wired correctly end to end, independent of any GitHub
  network access.

  Scenario: The test pipeline runs a trivial scenario
    Given the Athena test pipeline is wired up
    Then the pipeline reports success
