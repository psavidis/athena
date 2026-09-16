Feature: Keyboard control of Semantic Canvas zoom
  A reviewer controls the Semantic Canvas's zoom-altitude level entirely
  from the keyboard, without depending on a mouse wheel, trackpad, or
  pinch gesture (ticket #159).

  Scenario: Zooming in with the keyboard
    Given the reviewer is viewing the Semantic Canvas at the Capability+Flow stop
    When the reviewer uses the keyboard shortcut to zoom in
    Then the camera moves to a stop closer than Capability+Flow on the zoom-altitude rail

  Scenario: Zooming out with the keyboard
    Given the reviewer is viewing the Semantic Canvas at the Structure stop
    When the reviewer uses the keyboard shortcut to zoom out
    Then the camera moves to a stop farther out than Structure on the zoom-altitude rail

  Scenario: Resetting/fitting the view with the keyboard
    Given the reviewer has panned and zoomed the Semantic Canvas away from its default view
    When the reviewer uses the keyboard shortcut to reset the view
    Then the camera returns to the default fit view for the current PR

  Scenario: Returning to the previous semantic level with the keyboard
    Given the reviewer moved from the Capability+Flow stop to the Structure stop
    When the reviewer uses the keyboard shortcut to return to the previous semantic level
    Then the camera returns to the Capability+Flow stop

  Scenario: Jumping directly to a specific zoom-altitude stop with the keyboard
    Given the reviewer is viewing the Semantic Canvas at the Intent stop
    When the reviewer uses the keyboard shortcut for the Architecture stop
    Then the camera moves to the Architecture stop on the zoom-altitude rail

  Scenario: Keyboard zoom works without any mouse wheel, trackpad, or pinch input
    Given the reviewer has not touched the mouse wheel, trackpad, or a pinch gesture during the session
    When the reviewer uses only keyboard shortcuts to zoom in, zoom out, and reset the view
    Then each zoom action succeeds exactly as it would via mouse wheel, trackpad, or pinch
