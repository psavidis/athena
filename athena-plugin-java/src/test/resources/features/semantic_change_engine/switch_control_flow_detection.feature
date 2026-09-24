Feature: Switch statements and expressions in control-flow change detection
  `switch` entries are branches and their labels are conditions, just like
  if/else branches, so rewriting an if-chain as a switch is not mistaken for
  removed branches, and an added or removed case is a behavioral change
  (ticket #289; #258 re-run report, N2). Where only the branch structure
  changed, the description also says "same calls" (ticket #319).

  Scenario: An if-chain rewritten as a switch with the same conditions says so
    Given a base revision where method "describe" on class "Scope" picks a label with an if-chain over "EMPTY", "OPEN" and "CLOSED" with a final else
    And a head revision where "describe" on "Scope" picks the same labels with a switch over "EMPTY", "OPEN" and "CLOSED" with a default
    When the semantic engine detects transformations between the revisions
    Then the control-flow change of "Scope#describe" is described as "if-chain rewritten as switch; same calls"

  Scenario: An if-condition combining two values maps to two fall-through cases
    Given a base revision where method "describe" on class "Scope" picks a label with an if-chain where one branch covers both "OPEN" and "CLOSED"
    And a head revision where "describe" on "Scope" picks the same labels with a switch where "OPEN" falls through to "CLOSED"
    When the semantic engine detects transformations between the revisions
    Then the control-flow change of "Scope#describe" is described as "if-chain rewritten as switch; same calls"

  Scenario: A statement after an if-chain that becomes the switch default is still a rewrite
    Given a base revision where method "describe" on class "Scope" picks a label with an if-chain over "EMPTY" and "OPEN" and falls back after it
    And a head revision where "describe" on "Scope" picks a label with a switch over "EMPTY" and "OPEN" with a default
    When the semantic engine detects transformations between the revisions
    Then the control-flow change of "Scope#describe" is described as "if-chain rewritten as switch; same calls"

  Scenario: An added case is an added branch
    Given a base revision where method "describe" on class "Scope" picks a label with a switch over "EMPTY" and "OPEN" with a default
    And a head revision where "describe" on "Scope" picks a label with a switch over "EMPTY", "OPEN" and "CLOSED" with a default
    When the semantic engine detects transformations between the revisions
    Then the control-flow change of "Scope#describe" is described as "branch added"

  Scenario: A removed case is a removed branch
    Given a base revision where method "describe" on class "Scope" picks a label with a switch over "EMPTY", "OPEN" and "CLOSED" with a default
    And a head revision where "describe" on "Scope" picks a label with a switch over "EMPTY" and "OPEN" with a default
    When the semantic engine detects transformations between the revisions
    Then the control-flow change of "Scope#describe" is described as "branch removed"

  Scenario: A changed case label is a changed condition
    Given a base revision where method "describe" on class "Scope" picks a label with a switch over "EMPTY" and "OPEN" with a default
    And a head revision where "describe" on "Scope" picks a label with a switch over "EMPTY" and "CLOSED" with a default
    When the semantic engine detects transformations between the revisions
    Then the control-flow change of "Scope#describe" is described as "condition changed; same calls"

  Scenario: An added case in a switch expression is an added branch
    Given a base revision where method "describe" on class "Scope" returns a switch expression over "EMPTY" and "OPEN" with a default
    And a head revision where "describe" on "Scope" returns a switch expression over "EMPTY", "OPEN" and "CLOSED" with a default
    When the semantic engine detects transformations between the revisions
    Then the control-flow change of "Scope#describe" is described as "branch added; same calls"

  Scenario: An unchanged switch with a changed case body is not a control-flow change
    Given a base revision where method "describe" on class "Scope" picks a label with a switch over "EMPTY" and "OPEN" with a default
    And a head revision where "describe" on "Scope" picks different labels with a switch over "EMPTY" and "OPEN" with a default
    When the semantic engine detects transformations between the revisions
    Then no control-flow change is detected involving "Scope#describe"
