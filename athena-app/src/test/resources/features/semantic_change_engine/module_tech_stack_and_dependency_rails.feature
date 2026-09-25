Feature: Tech stack and dependency rails for Gradle and Maven modules
  Every territory shows its real tech stack and the dependencies between the
  project's modules, read from Gradle and Maven build files alike — including
  nested modules and single-module projects (ticket #293; #258 re-run report,
  R11).

  Scenario: A Gradle module that uses Spring Boot is a Spring Boot module
    Given the user has created a standalone Diff changing a class in Gradle module "module/web-server" whose build file applies Spring Boot
    When the user requests the Semantic Canvas topology
    Then territory "web-server" has tech stack "Spring Boot · Java"

  Scenario: A Kotlin-DSL Gradle module without Spring Boot is a Java module
    Given the user has created a standalone Diff changing a class in Kotlin-DSL Gradle module "module/validation"
    When the user requests the Semantic Canvas topology
    Then territory "validation" has tech stack "Java"

  Scenario: A Gradle project dependency is a dependency rail
    Given the user has created a standalone Diff changing a class in Gradle module "module/web-server" which depends on project ":module:core"
    When the user requests the Semantic Canvas topology
    Then a dependency rail runs from "web-server" to "core"
    And territory "core" is idle

  Scenario: A Gradle type-safe project accessor is a dependency rail
    Given the user has created a standalone Diff changing a class in Gradle module "module/web-server" which depends on "projects.module.springBootCore"
    When the user requests the Semantic Canvas topology
    Then a dependency rail runs from "web-server" to "spring-boot-core"

  Scenario: A nested Maven module depending on a sibling's artifactId is a dependency rail
    Given the user has created a standalone Diff changing a class in Maven module "modules/api" which depends on sibling module "modules/model" with artifactId "acme-model"
    When the user requests the Semantic Canvas topology
    Then a dependency rail runs from "api" to "model"

  Scenario: A single-module Gradle project gets its tech stack from the root build file
    Given the user has created a standalone Diff changing a class under "src/main/java" of a Gradle project named "petclinic"
    When the user requests the Semantic Canvas topology
    Then territory "petclinic" has tech stack "Java"

  # Ticket #314: tech stack and rails from build files named after their module.

  Scenario: A dependency declared in a build file named after its module is a dependency rail
    Given the user has created a standalone Diff changing a class in module "spring-webmvc" whose build file "spring-webmvc.gradle" depends on project ":spring-core"
    When the user requests the Semantic Canvas topology
    Then a dependency rail runs from "spring-webmvc" to "spring-core"
    And territory "spring-webmvc" has tech stack "Java"

  # Ticket #376: kafka-style builds configure each project from the root build file.

  Scenario: A dependency in a root project block is a rail from that project
    Given the user has created a standalone Diff of Gradle project "kafka" including "clients, streams" whose root build file reads "project(':streams') {\n  dependencies {\n    implementation project(':clients')\n  }\n}", changing a class in "streams"
    When the user requests the Semantic Canvas topology
    Then a dependency rail runs from "streams" to "clients"
    And territory "clients" is idle

  Scenario: A root project block for a nested project is a rail from that nested module
    Given the user has created a standalone Diff of Gradle project "kafka" including "streams, streams:integration-tests" whose root build file reads "project(':streams:integration-tests') {\n  dependencies {\n    testImplementation project(':streams')\n  }\n}", changing a class in "streams/integration-tests"
    When the user requests the Semantic Canvas topology
    Then a dependency rail runs from "integration-tests" to "streams"

  Scenario: Another project's block gives no rail to the changed module
    Given the user has created a standalone Diff of Gradle project "kafka" including "clients, core, streams" whose root build file reads "project(':core') {\n  dependencies {\n    implementation project(':clients')\n  }\n}", changing a class in "streams"
    When the user requests the Semantic Canvas topology
    Then no dependency rail is drawn

  Scenario: A dependency in a subprojects block is no rail
    Given the user has created a standalone Diff of Gradle project "kafka" including "clients, streams" whose root build file reads "subprojects {\n  dependencies {\n    implementation project(':clients')\n  }\n}", changing a class in "streams"
    When the user requests the Semantic Canvas topology
    Then no dependency rail is drawn

  Scenario: A change to the root project doesn't draw rails from the other projects' blocks
    Given the user has created a standalone Diff of Gradle project "kafka" including "clients, streams" whose root build file reads "project(':streams') {\n  dependencies {\n    implementation project(':clients')\n  }\n}", changing a class in "src/main/java"
    When the user requests the Semantic Canvas topology
    Then no dependency rail is drawn
