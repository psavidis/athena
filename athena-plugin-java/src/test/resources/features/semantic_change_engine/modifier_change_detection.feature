Feature: Modifier and visibility change detection
  A declaration whose modifiers changed (final, static, abstract,
  synchronized, volatile, transient) or whose visibility changed is reported,
  so a class made final or a method made less visible is never read as "no
  semantic change" (ticket #313; expansion baseline, F1).

  Scenario: A method made final is reported
    Given a base revision where class "Tester" has method "assertPermitted" with modifiers ""
    And a head revision where "Tester#assertPermitted" has modifiers "final"
    When the semantic engine detects transformations between the revisions
    Then the modifier change of "Tester#assertPermitted" reads "+final"

  Scenario: A method whose visibility narrowed is reported
    Given a base revision where class "LineBuffer" has method "add" with modifiers "protected"
    And a head revision where "LineBuffer#add" has modifiers ""
    When the semantic engine detects transformations between the revisions
    Then the modifier change of "LineBuffer#add" reads "protected -> package-private"

  Scenario: Visibility and keyword changes are described together
    Given a base revision where class "Registry" has method "lookup" with modifiers "public"
    And a head revision where "Registry#lookup" has modifiers "private static"
    When the semantic engine detects transformations between the revisions
    Then the modifier change of "Registry#lookup" reads "public -> private, +static"

  Scenario: A field made final is reported
    Given a base revision where class "Generator" has field "delegate" with modifiers "private"
    And a head revision where field "Generator#delegate" has modifiers "private final"
    When the semantic engine detects transformations between the revisions
    Then the modifier change of "Generator#delegate" reads "+final"

  Scenario: A class made final is reported
    Given a base revision where class "Helper" has modifiers "public"
    And a head revision where class "Helper" has modifiers "public final"
    When the semantic engine detects transformations between the revisions
    Then the modifier change of "Helper" reads "+final"

  Scenario: A constructor whose visibility widened is reported
    Given a base revision where class "Account" has a constructor with modifiers "private"
    And a head revision where the constructor of "Account" has modifiers "public"
    When the semantic engine detects transformations between the revisions
    Then the modifier change of "Account#<init>" reads "private -> public"

  Scenario: A modifier change alongside a body edit is reported as well as the body edit
    Given a base revision where class "Tester" has method "assertPermitted" with modifiers ""
    And a head revision where "Tester#assertPermitted" has modifiers "final" and a changed body
    When the semantic engine detects transformations between the revisions
    Then the modifier change of "Tester#assertPermitted" reads "+final"
    And a body modification is detected involving "Tester#assertPermitted" categorised as Unknown

  Scenario: Reordered modifiers are not a change
    Given a base revision where class "Registry" has method "lookup" with modifiers "public static final"
    And a head revision where "Registry#lookup" has modifiers "final public static"
    When the semantic engine detects transformations between the revisions
    Then no "CHANGE_MODIFIERS" transformation is detected involving "Registry#lookup"
