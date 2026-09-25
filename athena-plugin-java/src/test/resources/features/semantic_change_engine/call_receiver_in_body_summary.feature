Feature: A body summary names a call together with its receiver
  An added or removed call made on a plain name or a field of this class reads
  with that receiver, so a one-line fix like "consumedOffsets.clear()" reads as
  that, not as a bare "+clear". Calls on anything else keep their simple name
  (ticket #360; third expansion baseline, K2).

  Scenario: A call on a local name or parameter is named with it
    Given a base revision where method "commit" on class "Consumer" has the body "offsets.flush();"
    And a head revision where "commit" on "Consumer" has the body "offsets.flush(); consumedOffsets.clear();"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Consumer#commit" is described as "Modify body of Consumer#commit: +consumedOffsets.clear"

  Scenario: A call on a field of this class is named with the field
    Given a base revision where method "reset" on class "Registry" has the body "size = 0;"
    And a head revision where "reset" on "Registry" has the body "size = 0; this.cache.invalidate();"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Registry#reset" is described as "Modify body of Registry#reset: +cache.invalidate"

  Scenario: A removed call is named with its receiver
    Given a base revision where method "commit" on class "Consumer" has the body "offsets.flush(); consumedOffsets.clear();"
    And a head revision where "commit" on "Consumer" has the body "offsets.flush();"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Consumer#commit" is described as "Modify body of Consumer#commit: -consumedOffsets.clear"

  Scenario: Calls on a method result, a chain, super or a type keep their simple name
    Given a base revision where method "start" on class "Route" has the body "size = 0;"
    And a head revision where "start" on "Route" has the body "size = 0; getContext().trigger(); super.start(); java.util.Objects.requireNonNull(name); Objects.hash(name);"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Route#start" is described as "Modify body of Route#start: +getContext, +trigger, +start, +requireNonNull, +hash"

  Scenario: The same method called on two receivers counts as two items
    Given a base revision where method "reset" on class "Buffers" has the body "size = 0;"
    And a head revision where "reset" on "Buffers" has the body "size = 0; in.clear(); out.clear();"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Buffers#reset" is described as "Modify body of Buffers#reset: +in.clear, +out.clear"

  Scenario: A restructuring that calls the same methods on another receiver still makes the same calls
    Given a base revision where method "writeAll" on class "Serializer" has the body "for (int i = 0; i < props.length; ++i) { if (props[i] != null) { out.write(props[i]); } }"
    And a head revision where "writeAll" on "Serializer" has the body "Writer w = out; int i = 0; for (; i + 1 < props.length; i += 2) { if (props[i] != null) { w.write(props[i]); } if (props[i + 1] != null) { w.write(props[i + 1]); } } if (i < props.length && props[i] != null) { w.write(props[i]); }"
    When the semantic engine detects transformations between the revisions
    Then the control-flow change of "Serializer#writeAll" ends with "; same calls"
