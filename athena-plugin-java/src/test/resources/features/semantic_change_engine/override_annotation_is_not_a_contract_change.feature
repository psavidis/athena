Feature: @Override is not a contract change
  The Override annotation is a compiler check, not part of a method's
  contract, so adding or removing it alone is never reported as an
  annotation change (ticket #296; #258 re-run report, N5).

  Scenario: An @Override added to a method is not reported
    Given a base revision where method "getClientSession" on class "DeviceContext" has no annotations
    And a head revision where "DeviceContext#getClientSession" is annotated "@Override"
    When the semantic engine detects transformations between the revisions
    Then no "CHANGE_METHOD_ANNOTATIONS" transformation is detected involving "DeviceContext#getClientSession"

  Scenario: An @Override removed from a method is not reported
    Given a base revision where method "getClientSession" on class "DeviceContext" is annotated "@Override"
    And a head revision where "DeviceContext#getClientSession" has no annotations
    When the semantic engine detects transformations between the revisions
    Then no "CHANGE_METHOD_ANNOTATIONS" transformation is detected involving "DeviceContext#getClientSession"

  Scenario: An @Override added together with a contract annotation reports only the contract annotation
    Given a base revision where method "getClientSession" on class "DeviceContext" has no annotations
    And a head revision where "DeviceContext#getClientSession" is annotated "@Override @Deprecated"
    When the semantic engine detects transformations between the revisions
    Then the method annotation change of "DeviceContext#getClientSession" reads "+@Deprecated"
