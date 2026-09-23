Feature: Mechanical replacement detection is scoped to the change
  Mechanical replacements are found by looking only at what changed, so
  their detection cost follows the size of the change rather than the
  size of the repository, with the same results (ticket #270).

  Scenario: A consistent identifier replacement across changed files is still detected
    Given a base and head revision where identifier "LegacyClient" is replaced by "HttpClient" consistently in three changed files
    When the semantic engine detects transformations between the revisions
    Then a "MECHANICAL_REPLACEMENT" transformation is detected involving "LegacyClient -> HttpClient" with 3 occurrences

  Scenario: An identifier that appears only in unchanged files is not considered
    Given a base and head revision where identifier "Unrelated" appears only in files that are identical in both revisions
    When the semantic engine detects transformations between the revisions
    Then no "MECHANICAL_REPLACEMENT" transformation is detected involving "Unrelated"

  Scenario: A replacement is not reported when one changed file replaces the identifier inconsistently
    Given a base and head revision where "LegacyClient" is replaced by "HttpClient" in one changed file and by "WebClient" in another
    When the semantic engine detects transformations between the revisions
    Then no "MECHANICAL_REPLACEMENT" transformation is detected involving "LegacyClient"

  Scenario: Unchanged files do not count as occurrences
    Given a base and head revision where "LegacyClient" is replaced by "HttpClient" in two changed files
    And "LegacyClient" is still referenced unchanged in a third file
    When the semantic engine detects transformations between the revisions
    Then a "MECHANICAL_REPLACEMENT" transformation is detected involving "LegacyClient -> HttpClient" with 2 occurrences
