Feature: Module-level Change grouping
  A reviewer's "why was this touched?" question is usually answered at the
  scope of a whole module (e.g. "the new crowdness-live module"), not one
  class at a time — a reason can span many classes and files at once. The
  Semantic Change Engine clusters Changes by their top-level module so that
  scope is available before any AI narrative is layered on top of it.

  Scenario: Changes touching files under the same top-level module are grouped together
    Given Changes touching files under modules "crowdness-live" and "crowdness-ingestion"
    When the semantic engine groups the Changes by module
    Then the module group for "crowdness-live" contains those Changes
    And the module group for "crowdness-ingestion" contains those Changes

  Scenario: A Change touching a file with no module segment falls into a root group
    Given a Change touching a file with no module segment
    When the semantic engine groups the Changes by module
    Then the module group for "(root)" contains that Change
