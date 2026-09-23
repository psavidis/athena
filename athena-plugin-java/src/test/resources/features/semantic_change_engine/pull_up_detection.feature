Feature: Members pulled up into a new base class
  When several existing classes start extending a newly added base class and
  each loses the same member that the base class gains, the reviewer sees one
  pull-up naming every source class, not one move plus a pile of removals —
  and the same revisions always give the same result (ticket #290; #258
  re-run report, N3).

  Scenario: A field pulled up from several classes is one pull-up
    Given a base revision where classes "DeviceContext", "BackchannelContext" and "ImplicitContext" each declare field "builder" of type "Builder"
    And a head revision where new class "AbstractContext" declares field "builder" of type "Builder" and those classes extend it without the field
    When the semantic engine detects transformations between the revisions
    Then the change "Pull up field builder into AbstractContext (from BackchannelContext, DeviceContext, ImplicitContext)" is detected
    And no removal, move or addition of "builder" is reported

  Scenario: A method pulled up from several classes is one pull-up
    Given a base revision where classes "DeviceContext" and "BackchannelContext" each declare method "getBuilder" returning "Builder"
    And a head revision where new class "AbstractContext" declares method "getBuilder" returning "Builder" and those classes extend it without the method
    When the semantic engine detects transformations between the revisions
    Then the change "Pull up getBuilder into AbstractContext (from BackchannelContext, DeviceContext)" is detected
    And no removal, move or addition of "getBuilder" is reported

  Scenario: A member pulled up into a new base class two levels up is still a pull-up
    Given a base revision where classes "DeviceContext" and "BackchannelContext" each declare field "builder" of type "Builder"
    And a head revision where new class "AbstractContext" declares field "builder" of type "Builder" and those classes extend new class "AbstractSessionContext" which extends it, without the field
    When the semantic engine detects transformations between the revisions
    Then the change "Pull up field builder into AbstractContext (from BackchannelContext, DeviceContext)" is detected

  Scenario: A member moved out of a single class is a move, not a pull-up
    Given a base revision where classes "DeviceContext" and "BackchannelContext" each declare field "builder" of type "Builder"
    And a head revision where new class "AbstractContext" declares field "builder" of type "Builder" and only "DeviceContext" extends it without the field
    When the semantic engine detects transformations between the revisions
    Then no pull-up is detected

  Scenario: A subclass that keeps its member is not a source of the pull-up
    Given a base revision where classes "DeviceContext", "BackchannelContext" and "ImplicitContext" each declare field "builder" of type "Builder"
    And a head revision where new class "AbstractContext" declares field "builder" of type "Builder" and those classes extend it, but "ImplicitContext" keeps its own field
    When the semantic engine detects transformations between the revisions
    Then the change "Pull up field builder into AbstractContext (from BackchannelContext, DeviceContext)" is detected

  Scenario: The same revisions always give the same transformations
    Given a base revision where classes "DeviceContext", "BackchannelContext" and "ImplicitContext" each declare field "builder" of type "Builder"
    And a head revision where new class "AbstractContext" declares field "builder" of type "Builder" and only "DeviceContext" extends it without the field
    When the semantic engine detects transformations between the revisions twice
    Then both runs report the same transformations in the same order
