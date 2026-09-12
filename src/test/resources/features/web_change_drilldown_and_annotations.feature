Feature: Web Change drill-down, comments & private notes
  Once a reviewer has the Change Map, they can open a Change's detail view
  and attach comments (to sync to GitHub later) or private notes (never
  synced) at line, symbol, Change, or review scope (ticket #75).

  Scenario: Reviewer opens a Change's detail view from the Change Map
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    When the reviewer opens the detail view of the rename Change
    Then the detail view shows the Change's category "STRUCTURAL"
    And the detail view shows the Change's description
    And the detail view lists the involved symbols
    And the detail view lists the touched files
    And the detail view exposes the Change's underlying diff

  Scenario: Opening a detail view for a Change that no longer matches any detected Change fails
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    When the reviewer opens the detail view of a Change id that does not match any current Change
    Then the request is rejected because the Change was not found

  Scenario: Reviewer adds a comment at Change scope
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    When the reviewer adds the comment "Good refactor" at the rename Change's scope
    Then the rename Change's comments include "Good refactor"

  Scenario: Reviewer adds a private note that never appears among comments
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    When the reviewer adds the private note "Ask the author about this" at the rename Change's scope
    Then the rename Change's private notes include "Ask the author about this"
    And the rename Change's comments do not include "Ask the author about this"

  Scenario: Reviewer adds a comment at line scope
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    When the reviewer adds the comment "Why here?" at line 12 of file "Greeter.java"
    Then the comments at line 12 of file "Greeter.java" include "Why here?"

  Scenario: Reviewer adds a comment at symbol scope
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    When the reviewer adds the comment "Needs a Javadoc" at symbol "Greeter#greet"
    Then the comments at symbol "Greeter#greet" include "Needs a Javadoc"

  Scenario: Reviewer adds a comment at review scope
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    When the reviewer adds the comment "Overall looks solid" at review scope
    Then the review's comments include "Overall looks solid"

  Scenario: Adding a blank comment is rejected
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    When the reviewer attempts to add a blank comment at the rename Change's scope
    Then the attempt is rejected as invalid

  Scenario: Requesting a Change's detail view without connecting to GitHub fails
    Given the reviewer has not connected to GitHub
    When the reviewer opens the detail view of any Change
    Then the request is rejected as unauthorized

  Scenario: Requesting a Change's detail view before selecting a PR fails
    Given the reviewer is connected to GitHub
    But the reviewer has not selected a PR
    When the reviewer opens the detail view of any Change
    Then the request is rejected because no PR is selected
