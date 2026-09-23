Feature: Enum constant and annotation element detection
  Added and removed enum constants and annotation-type elements are
  reported as Changes, so public API additions such as a new event type
  or a new annotation option are part of the review (ticket #266).

  Scenario: An added enum constant is detected
    Given a base revision where enum "ClientPolicyEvent" has constants "TOKEN_EXCHANGE_REQUEST" and "DEVICE_TOKEN_RESPONSE"
    And a head revision where "ClientPolicyEvent" also has "TOKEN_EXCHANGE_RESPONSE"
    When the semantic engine detects transformations between the revisions
    Then an enum constant addition is detected involving "ClientPolicyEvent#TOKEN_EXCHANGE_RESPONSE"

  Scenario: A removed enum constant is detected
    Given a base revision where enum "Status" has constants "ACTIVE", "LEGACY" and "DISABLED"
    And a head revision where "Status" no longer has "LEGACY"
    When the semantic engine detects transformations between the revisions
    Then an enum constant removal is detected involving "Status#LEGACY"

  Scenario: Reordering enum constants is not reported as additions or removals
    Given a base and head revision where enum "Status" has the same constants in a different order
    When the semantic engine detects transformations between the revisions
    Then no enum constant addition or removal is detected involving "Status"

  Scenario: An added annotation element with a default is detected
    Given a base revision where annotation "Spy" has no elements
    And a head revision where annotation "Spy" has element "mockMaker" of type "String" with default ""
    When the semantic engine detects transformations between the revisions
    Then an annotation element addition is detected involving "Spy#mockMaker"
    And the detection shows its default value ""

  Scenario: A removed annotation element is detected
    Given a base revision where annotation "Retry" has elements "attempts" and "delay"
    And a head revision where annotation "Retry" only has "attempts"
    When the semantic engine detects transformations between the revisions
    Then an annotation element removal is detected involving "Retry#delay"

  Scenario: A changed annotation element default is detected
    Given a base revision where annotation "Retry" has element "attempts" with default 3
    And a head revision where "attempts" on "Retry" has default 5
    When the semantic engine detects transformations between the revisions
    Then an annotation element default change is detected involving "Retry#attempts" from 3 to 5
