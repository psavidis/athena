Feature: Maven dependency changes
  A pom.xml's dependency changes are Changes of their own, so a dependency
  cleanup across many modules reads as what it is rather than as unsupported
  files (ticket #340; second expansion baseline, H5).

  Scenario: A dependency added to a module is reported
    Given a Maven module "dropwizard-views" whose pom gains a dependency on "org.glassfish:jakarta.el"
    When Athena analyzes the pom change
    Then the change "Add dependency org.glassfish:jakarta.el (dropwizard-views)" is reported

  Scenario: A dependency removed from a module is reported
    Given a Maven module "dropwizard-views" whose pom loses its dependency on "org.glassfish:jakarta.el"
    When Athena analyzes the pom change
    Then the change "Remove dependency org.glassfish:jakarta.el (dropwizard-views)" is reported

  Scenario: A changed dependency scope is reported
    Given a Maven module "dropwizard-core" whose dependency on "org.slf4j:slf4j-api" changes scope from "test" to "compile"
    When Athena analyzes the pom change
    Then the change "Change dependency org.slf4j:slf4j-api (dropwizard-core): scope test -> compile" is reported

  Scenario: A changed managed dependency version is reported
    Given a Maven module "dropwizard-bom" whose managed dependency on "io.netty:netty-codec" changes version from "4.1.0" to "4.2.0"
    When Athena analyzes the pom change
    Then the change "Change managed dependency io.netty:netty-codec (dropwizard-bom): version 4.1.0 -> 4.2.0" is reported

  Scenario: An exclusion dropped from a dependency is reported
    Given a Maven module "dropwizard-lifecycle" whose dependency on "org.eclipse.jetty:jetty-server" drops its exclusion of "org.eclipse.jetty.toolchain:jetty-servlet-api"
    When Athena analyzes the pom change
    Then the change "Change dependency org.eclipse.jetty:jetty-server (dropwizard-lifecycle): exclusion org.eclipse.jetty.toolchain:jetty-servlet-api removed" is reported

  Scenario: A pom whose dependencies did not change is not reported as a dependency change
    Given a Maven module "dropwizard-core" whose pom only changes its description
    When Athena analyzes the pom change
    Then no dependency change is reported
    And its pom.xml is listed as unrepresented with reason "no semantic change detected"
