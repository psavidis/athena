Feature: Search and filtering (MVP subset)
  A reviewer working through a large PR can search and filter the Change
  Map/Review Queue instead of scrolling through every Change (epic #5
  §42, §43). MVP scope: search by Change description/category, files,
  symbols, and comments; filter by category, review state, and symbol.

  Scenario: Searching by Change description finds a matching Change
    Given a searchable PR with a rename Change and a mechanical replacement Change
    When the reviewer searches for "salute"
    Then the search results include the rename Change
    And the search results do not include the mechanical replacement Change

  Scenario: Searching by file name finds the Change touching that file
    Given a searchable PR with a rename Change and a mechanical replacement Change
    When the reviewer searches for "Greeter.java"
    Then the search results include the rename Change

  Scenario: Searching by symbol finds the Change involving that symbol
    Given a searchable PR with a rename Change and a mechanical replacement Change
    When the reviewer searches for "Greeter#greet"
    Then the search results include the rename Change

  Scenario: Searching by comment text finds the Change it's attached to
    Given a searchable PR with a rename Change and a mechanical replacement Change
    And a comment "Please double check this rename" attached to the rename Change
    When the reviewer searches for "double check"
    Then the search results include the rename Change

  Scenario: A search with no matches returns an empty result, not an error
    Given a searchable PR with a rename Change and a mechanical replacement Change
    When the reviewer searches for "nonexistent-term-xyz"
    Then the search results are empty

  Scenario: A comment remains findable after the Change is recomputed into a new instance
    Given a searchable PR with a rename Change and a mechanical replacement Change
    And a comment "Please double check this rename" attached to the rename Change
    When the same PR is re-analyzed, producing new Change instances for the same transformations
    And the reviewer searches for "double check"
    Then the search results include the rename Change

  Scenario: Filtering by category narrows the Change Map to that category
    Given a searchable PR with a rename Change and a mechanical replacement Change
    When the reviewer filters by category "Structural"
    Then the filtered results include the rename Change
    And the filtered results do not include the mechanical replacement Change

  Scenario: Filtering by review state narrows the Change Map to that state
    Given a searchable PR with a rename Change and a mechanical replacement Change
    And the reviewer has reviewed the rename Change in the search fixture
    When the reviewer filters by review state "Reviewed"
    Then the filtered results include the rename Change
    And the filtered results do not include the mechanical replacement Change

  Scenario: Filtering by symbol narrows the Change Map to Changes involving that symbol
    Given a searchable PR with a rename Change and a mechanical replacement Change
    When the reviewer filters by symbol "Greeter#greet"
    Then the filtered results include the rename Change
    And the filtered results do not include the mechanical replacement Change
