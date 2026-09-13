Feature: Web Change drill-down, comments & private notes
  Once a reviewer has the Change Map, they can request a Change's detail
  view and attach comments (to sync to GitHub later) or private notes
  (never synced) at line, symbol, Change, or review scope (ticket #75).

  Scenario: Reviewer requests a Change's detail view from the Change Map
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    When the reviewer requests the detail view of the rename Change
    Then the detail response shows the Change's category "STRUCTURAL"
    And the detail response shows the Change's description
    And the detail response lists the involved symbols
    And the detail response lists the touched files
    And the detail response includes a diff field, present regardless of classification

  Scenario: Requesting a detail view for a Change that no longer matches any detected Change fails
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    When the reviewer requests the detail view of a Change key that does not match any current Change
    Then the request is rejected because the Change was not found

  Scenario: Reviewer posts a comment at Change scope
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    When the reviewer posts the comment "Good refactor" scoped to the rename Change
    Then the response's comments include "Good refactor"

  Scenario: Reviewer posts a private note that never appears among comments
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    When the reviewer posts the private note "Ask the author about this" scoped to the rename Change
    Then the response's private notes include "Ask the author about this"
    And the response's comments do not include "Ask the author about this"

  Scenario: Reviewer posts a comment at line scope
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    When the reviewer posts the comment "Why here?" scoped to line 12 of file "Greeter.java"
    Then the response's comments include "Why here?"

  Scenario: Reviewer posts a comment at symbol scope
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    When the reviewer posts the comment "Needs a Javadoc" scoped to symbol "Greeter#greet"
    Then the response's comments include "Needs a Javadoc"

  Scenario: Reviewer posts a comment at review scope
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    When the reviewer posts the comment "Overall looks solid" scoped to the whole review
    Then the response's comments include "Overall looks solid"

  Scenario: Posting a blank comment is rejected
    Given the reviewer is connected to GitHub
    And the reviewer has selected a PR whose base and head revisions differ by a rename
    And the reviewer has requested the Change Map
    When the reviewer posts a blank comment scoped to the rename Change
    Then the request is rejected as a bad request

  Scenario: Requesting a Change's detail view without connecting to GitHub fails
    Given the reviewer has not connected to GitHub
    When the reviewer requests the detail view of any Change
    Then the request is rejected as unauthorized

  Scenario: Requesting a Change's detail view before selecting a PR fails
    Given the reviewer is connected to GitHub
    But the reviewer has not selected a PR
    When the reviewer requests the detail view of any Change
    Then the request is rejected because no PR is selected
