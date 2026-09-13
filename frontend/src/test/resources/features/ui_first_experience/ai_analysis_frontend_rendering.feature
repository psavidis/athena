Feature: AI analysis trigger & findings review rendering (frontend)
  A reviewer inspects the context boundary, triggers AI analysis, and
  accepts or dismisses findings, all from the browser (ticket #77).

  Scenario: A reviewer sees the context boundary before triggering analysis
    Given the reviewer is viewing the Change Map
    When the reviewer opens the AI analysis view
    Then the view shows which Changes are included
    And the view shows which Changes are excluded and why

  Scenario: A reviewer triggers AI analysis and sees the findings
    Given the reviewer is viewing the AI analysis view
    When the reviewer triggers AI analysis
    Then the findings list shows each finding as pending

  Scenario: A reviewer accepts a finding
    Given the reviewer is viewing AI findings including "You may have missed the null check"
    When the reviewer accepts that finding
    Then that finding is shown as accepted

  Scenario: A reviewer dismisses a finding
    Given the reviewer is viewing AI findings including "You may have missed the null check"
    When the reviewer dismisses that finding
    Then that finding is shown as dismissed

  Scenario: A reviewer jumps from a finding to its related Change
    Given the reviewer is viewing a finding related to the Change "Rename greet to salute"
    When the reviewer clicks the finding's jump target
    Then the reviewer sees that Change's detail view
