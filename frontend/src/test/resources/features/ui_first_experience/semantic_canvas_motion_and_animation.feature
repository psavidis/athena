Feature: Semantic Canvas — motion and animation pass
  The Semantic Canvas's camera dives, node reveals, new-module pulses, and
  connector highlights (ticket #133) read as purposeful spatial motion
  rather than mechanical or instant cuts — while never blocking
  interaction, and always collapsing to near-zero duration under
  prefers-reduced-motion.

  Scenario: The camera dive into a territory uses an eased transition
    Given the reviewer is viewing the Semantic Canvas territory map
    When the reviewer clicks a territory
    Then the camera dive animates with an ease-out transition over roughly 500-700ms

  Scenario: Nodes revealed at a zoom-altitude stop animate in staggered
    Given the reviewer has dived into a territory with multiple Structure-level nodes
    When the reviewer selects the Structure stop on the zoom-altitude rail
    Then the revealed nodes animate in with a staggered scale/fade rather than appearing all at once

  Scenario: A newly introduced module's territory carries an ambient pulse
    Given a PR that introduces the "crowdness-live" module for the first time
    When the reviewer is viewing the territory map
    Then the "crowdness-live" territory shows an ambient pulse

  Scenario: A merely-touched territory does not pulse
    Given a PR that modifies the existing "crowdness-ingestion" module
    When the reviewer is viewing the territory map
    Then the "crowdness-ingestion" territory does not show an ambient pulse

  Scenario: A connector touching the current selection animates with a flowing dash pattern
    Given the reviewer is viewing the territory map with a dependency rail between "crowdness-live" and "crowdness-connect"
    When the reviewer selects the "crowdness-live" territory
    Then the dependency rail touching "crowdness-live" shows a flowing dash animation

  Scenario: A connector not touching the current selection stays static
    Given the reviewer is viewing the territory map with dependency rails "crowdness-live" to "crowdness-connect" and "crowdness-ui" to "crowdness-management"
    When the reviewer selects the "crowdness-live" territory
    Then the dependency rail between "crowdness-ui" and "crowdness-management" stays static

  Scenario: All motion collapses to near-zero duration under reduced motion
    Given the reviewer has requested reduced motion
    When the reviewer dives into a territory and selects a zoom-altitude stop
    Then the camera dive, the node reveal, the new-module pulse, and the connector highlight all apply their end state immediately, without an animated transition

  Scenario: Clicking through camera dives in quick succession never gets the canvas stuck
    Given the reviewer is viewing the Semantic Canvas territory map
    When the reviewer clicks one territory, then immediately clicks a different territory before the first dive finishes
    Then the camera ends up focused on the second territory
    And the canvas remains interactive throughout
