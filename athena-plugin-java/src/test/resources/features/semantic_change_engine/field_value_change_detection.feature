Feature: Field value change detection
  A field whose initializer changed is reported, so a feature switched on by
  editing a constant table is never invisible (ticket #316; expansion
  baseline, F2).

  Scenario: An element added to a constant array is named
    Given a base revision where class "PeriodicityType" has field "VALID_ORDERED_LIST" initialized to "{ TOP_OF_HOUR, TOP_OF_DAY }"
    And a head revision where field "PeriodicityType#VALID_ORDERED_LIST" is initialized to "{ TOP_OF_HOUR, HALF_DAY, TOP_OF_DAY }"
    When the semantic engine detects transformations between the revisions
    Then the value change of "PeriodicityType#VALID_ORDERED_LIST" reads "+HALF_DAY"

  Scenario: Elements added to and removed from a List.of call are named
    Given a base revision where class "Defaults" has field "NAMES" initialized to "List.of(\"a\", \"b\")"
    And a head revision where field "Defaults#NAMES" is initialized to "List.of(\"b\", \"c\")"
    When the semantic engine detects transformations between the revisions
    Then the value change of "Defaults#NAMES" reads "+\"c\", -\"a\""

  Scenario: A changed scalar value shows both values
    Given a base revision where class "Limits" has field "MAX_SIZE" initialized to "100"
    And a head revision where field "Limits#MAX_SIZE" is initialized to "200"
    When the semantic engine detects transformations between the revisions
    Then the value change of "Limits#MAX_SIZE" reads "100 -> 200"

  Scenario: A value change is categorised as Unknown
    Given a base revision where class "Limits" has field "MAX_SIZE" initialized to "100"
    And a head revision where field "Limits#MAX_SIZE" is initialized to "200"
    When the semantic engine detects transformations between the revisions
    Then the value change of "Limits#MAX_SIZE" is categorised as Unknown

  Scenario: A whitespace-only change to an initializer is not a value change
    Given a base revision where class "Defaults" has field "NAMES" initialized to "List.of(\"a\",\"b\")"
    And a head revision where field "Defaults#NAMES" is initialized to "List.of( \"a\", \"b\" )"
    When the semantic engine detects transformations between the revisions
    Then no "CHANGE_FIELD_VALUE" transformation is detected involving "Defaults#NAMES"
