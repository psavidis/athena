Feature: Semantic Canvas — pan/zoom shell and module territory map
  The three-pane Semantic Change Explorer shell is replaced by a
  full-viewport spatial canvas (ticket #129). On load, the canvas shows a
  territory map: one region per module the PR touches or references,
  sized and positioned to reflect real dependency shape, so a reviewer
  sees where a change physically lands before diving into any one part
  of it.

  Scenario: The reviewer sees one territory per module affected by the PR
    Given the reviewer opens the Semantic Canvas for a PR that touches the "crowdness-live" and "crowdness-ingestion" modules
    Then the canvas shows a "crowdness-live" territory
    And the canvas shows a "crowdness-ingestion" territory

  Scenario: A territory introduced by this PR is shown as new
    Given the reviewer is viewing the Semantic Canvas for a PR that introduces the "crowdness-live" module for the first time
    Then the "crowdness-live" territory is marked as a new module

  Scenario: A territory modified by this PR is shown as touched, with real status text
    Given the reviewer is viewing the Semantic Canvas for a PR that modifies 17 files in "crowdness-ingestion", adding one new query
    Then the "crowdness-ingestion" territory is marked as touched
    And the "crowdness-ingestion" territory shows the status text "17 files · one new query"

  Scenario: A territory only referenced by the PR, not changed, is shown as idle
    Given the reviewer is viewing the Semantic Canvas for a PR whose changed modules call into the unmodified "crowdness-common" module
    Then the "crowdness-common" territory is marked as idle
    And the "crowdness-common" territory is not marked as touched or new

  Scenario: Each territory shows a tech-stack badge reflecting its real technology
    Given the reviewer is viewing the Semantic Canvas for a PR touching the Spring Boot module "crowdness-live" and the React module "crowdness-ui"
    Then the "crowdness-live" territory shows a "Spring Boot · Java" tech-stack badge
    And the "crowdness-ui" territory shows a "React · TypeScript" tech-stack badge

  Scenario: A dependency rail is drawn between two territories that depend on each other
    Given the reviewer is viewing the Semantic Canvas for a PR where "crowdness-live" depends on "crowdness-connect"
    Then a dependency rail is drawn between the "crowdness-live" territory and the "crowdness-connect" territory

  Scenario: No dependency rail is drawn between territories with no real dependency
    Given the reviewer is viewing the Semantic Canvas for a PR where "crowdness-live" and "crowdness-management" have no dependency on each other
    Then no dependency rail is drawn between the "crowdness-live" territory and the "crowdness-management" territory

  Scenario: Clicking a territory maximizes it with an animated camera dive
    Given the reviewer is viewing the Semantic Canvas territory map
    When the reviewer clicks the "crowdness-live" territory
    Then the camera animates into the "crowdness-live" territory
    And the "crowdness-live" territory fills nearly the entire viewport

  Scenario: The camera dive is skipped when the reviewer prefers reduced motion
    Given the reviewer has requested reduced motion
    And the reviewer is viewing the Semantic Canvas territory map
    When the reviewer clicks the "crowdness-live" territory
    Then the "crowdness-live" territory fills nearly the entire viewport immediately, without an animated transition

  Scenario: The reviewer pans the canvas by dragging
    Given the reviewer is viewing the Semantic Canvas territory map
    When the reviewer drags the canvas
    Then the visible territories shift to follow the drag

  Scenario: The reviewer zooms the canvas with the scroll wheel
    Given the reviewer is viewing the Semantic Canvas territory map
    When the reviewer scrolls to zoom in on the canvas
    Then the canvas scale increases

  Scenario: The reviewer zooms using the on-screen zoom controls
    Given the reviewer is viewing the Semantic Canvas territory map
    When the reviewer clicks the zoom-in control
    Then the canvas scale increases
    When the reviewer clicks the reset control
    Then the canvas returns to its initial pan and scale

  Scenario: Zoom is clamped so the canvas cannot scale beyond its bounds
    Given the reviewer is viewing the Semantic Canvas territory map
    When the reviewer zooms in repeatedly past the maximum scale
    Then the canvas scale does not exceed the maximum allowed scale
