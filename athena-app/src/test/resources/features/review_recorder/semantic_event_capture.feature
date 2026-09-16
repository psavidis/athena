Feature: Semantic event capture during a Review Recording
  While a Review Recording is active, Athena captures the meaningful
  things happening during the review — canvas navigation, semantic zoom
  changes, entities inspected, code/diff areas viewed, comments created —
  as a structured, timestamped event stream, so the session produces more
  than a raw log (ticket #204). This ticket captures events only; it does
  not yet extract "moments" (question/decision/concern), and does not use
  audio/transcript.

  Scenario: Canvas navigation is captured as a semantic event
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    When "Petros" navigates the canvas to the "PaymentValidator" component
    Then the recording's event stream includes a navigation event for the "PaymentValidator" component

  Scenario: A semantic zoom change is captured as a semantic event
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    When "Petros" changes the semantic zoom to "ARCHITECTURE"
    Then the recording's event stream includes a semantic zoom event to "ARCHITECTURE"

  Scenario: Inspecting an entity is captured as a semantic event
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    When "Petros" inspects the "OrderService" entity
    Then the recording's event stream includes an entity-inspected event for the "OrderService" entity

  Scenario: Viewing a code/diff area is captured as a semantic event
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    When "Petros" views the diff area for "src/main/java/OrderService.java"
    Then the recording's event stream includes a diff-area-viewed event for "src/main/java/OrderService.java"

  Scenario: Creating a comment is captured as a semantic event
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    When "Petros" creates the comment "Needs a null check" on the "OrderService" component
    Then the recording's event stream includes a comment-created event referencing the "OrderService" component

  Scenario: Multiple events are captured in the order they occurred
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    When "Petros" navigates the canvas to the "PaymentValidator" component
    And "Petros" inspects the "OrderService" entity
    Then the recording's event stream lists the navigation event before the entity-inspected event

  Scenario: Capturing an event on a recording that is not active is rejected
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" has stopped the recording
    When "Petros" attempts to navigate the canvas to the "PaymentValidator" component
    Then the review recording request is rejected as invalid
