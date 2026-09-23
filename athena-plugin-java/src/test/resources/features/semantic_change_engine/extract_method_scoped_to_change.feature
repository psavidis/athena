Feature: Extract-method detection only cites the change under review
  An extracted method is only reported when its caller is part of the
  change, and a new empty or trivial method is an addition, never an
  extraction, so no Change ever cites a file the change did not touch
  (ticket #269).

  Scenario: A genuine extraction within the change is still detected
    Given a base revision where class "Greeter" has a method "greet" with an inline fragment
    And a head revision where that fragment has been extracted into a new method "buildGreeting" called from "greet"
    When the semantic engine detects transformations between the revisions
    Then an "EXTRACT_METHOD" transformation is detected involving "Greeter#greet" and "Greeter#buildGreeting"

  Scenario: A new method is not reported as extracted from an unchanged file that calls a same-named method
    Given a base and head revision where unchanged class "Distribution" has a method that calls "close()" on a stream
    And the head revision adds class "ExecutorFactory" with a new method "close"
    When the semantic engine detects transformations between the revisions
    Then no "EXTRACT_METHOD" transformation is detected involving "ExecutorFactory#close"
    And an "ADD_SYMBOL" transformation is detected involving "ExecutorFactory#close"
    And no transformation cites a file of class "Distribution"

  Scenario: A new empty method is an addition, not an extraction
    Given a base revision where class "ExecutorFactory" does not exist
    And a head revision where class "ExecutorFactory" has an empty method "init" and a changed method "create" that calls "init()"
    When the semantic engine detects transformations between the revisions
    Then no "EXTRACT_METHOD" transformation is detected involving "ExecutorFactory#init"
    And an "ADD_SYMBOL" transformation is detected involving "ExecutorFactory#init"

  Scenario: No transformation cites a file that is identical in both revisions
    Given a base and head revision where files "A.java" and "B.java" changed and every other file is identical
    When the semantic engine detects transformations between the revisions
    Then every detected transformation only cites "A.java" or "B.java"
