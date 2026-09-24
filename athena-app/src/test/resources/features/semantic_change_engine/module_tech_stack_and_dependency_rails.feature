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
