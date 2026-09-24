Feature: Module identity comes from build descriptors
  A Semantic Canvas territory is a real module of the project: the nearest
  directory holding a build descriptor (pom.xml, build.gradle,
  build.gradle.kts, package.json) above a changed file — not the file's
  first path segment (ticket #292; #258 re-run report, R3).

  Scenario: Nested Gradle modules are separate territories named after their directories
    Given the user has created a standalone Diff changing classes in Gradle modules "module/web-server" and "module/validation"
    When the user requests the Semantic Canvas topology
    Then the territories are "web-server" and "validation"

  Scenario: A single-module Maven project is named after its artifactId
    Given the user has created a standalone Diff changing a class under "src/main/java" of a Maven project with artifactId "commons-lang3"
    When the user requests the Semantic Canvas topology
    Then the territories are "commons-lang3"

  Scenario: A Maven module's parent artifactId is not mistaken for its own
    Given the user has created a standalone Diff changing a class under "src/main/java" of a Maven project with artifactId "child-lib" and parent artifactId "parent-pom"
    When the user requests the Semantic Canvas topology
    Then the territories are "child-lib"

  Scenario: A single-module Gradle project is named after its root project name
    Given the user has created a standalone Diff changing a class under "src/main/java" of a Gradle project named "petclinic"
    When the user requests the Semantic Canvas topology
    Then the territories are "petclinic"

  Scenario: A root build descriptor without a name gives the root territory
    Given the user has created a standalone Diff changing a class under "src/main/java" of a Gradle project with no name
    When the user requests the Semantic Canvas topology
    Then the territories are "(root)"

  Scenario: Test sources belong to the same module as their production sources
    Given the user has created a standalone Diff changing a production class and a test class of Gradle module "module/web-server"
    When the user requests the Semantic Canvas topology
    Then the territories are "web-server"

  Scenario: Without any build descriptor the leading directory still names the territory
    Given the user has created a standalone Diff changing a class under "core/src/main/java" with no build descriptor anywhere
    When the user requests the Semantic Canvas topology
    Then the territories are "core"

  Scenario: The module-level semantic profile is found by the module's name
    Given the user has created a standalone Diff changing classes in Gradle modules "module/web-server" and "module/validation"
    When the user requests the semantic profile of module "web-server"
    Then the module's semantic profile has at least one entry

  # Ticket #314: Gradle builds that name each module's build file after the module.

  Scenario: A module whose build file is named after its directory is a module
    Given the user has created a standalone Diff changing classes in modules "junit-jupiter-api" and "junit-jupiter-engine" whose build files are named "<module>.gradle.kts"
    When the user requests the Semantic Canvas topology
    Then the territories are "junit-jupiter-api" and "junit-jupiter-engine"

  Scenario: A Groovy build file named after its directory is a module too
    Given the user has created a standalone Diff changing classes in modules "hibernate-core" and "hibernate-envers" whose build files are named "<module>.gradle"
    When the user requests the Semantic Canvas topology
    Then the territories are "hibernate-core" and "hibernate-envers"

  # Ticket #317: module names are unique within a repository.

  Scenario: Module directories that share a name get distinguishing names
    Given the user has created a standalone Diff changing classes in Maven modules "guava" and "android/guava"
    When the user requests the Semantic Canvas topology
    Then the territories are "guava" and "guava (android)"

  Scenario: Each same-named module's profile is its own
    Given the user has created a standalone Diff changing classes in Maven modules "guava" and "android/guava"
    When the user requests the semantic profile of module "guava (android)"
    Then the module's semantic profile covers only files under "android/guava"
