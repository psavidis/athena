Feature: Semantic Canvas — zoom-altitude rail across the six semantic dimensions
  The six semantic dimensions (Intent, Capability+Flow, Architecture,
  Pattern+Framework, Structure, Code-as-evidence) become literal
  camera-zoom stops on the canvas instead of an always-visible navigation
  rail (ticket #130), so a reviewer only sees the kind of meaning they're
  currently looking at.

  Scenario: The altitude rail compresses to only the dimensions this PR has content for
    Given the reviewer is viewing the Semantic Canvas for a PR classified only at Intent and Structure
    Then the zoom-altitude rail shows only the Intent and Structure stops

  Scenario: Selecting the Intent stop moves the camera to the PR's headline
    Given the reviewer is viewing the Semantic Canvas for a PR whose Intent is classified as "Enforce payment expiration"
    When the reviewer selects the Intent stop on the zoom-altitude rail
    Then the camera moves to the PR's "Enforce payment expiration" headline node

  Scenario: Selecting the Capability+Flow stop shows the territory map and its flow nodes
    Given the reviewer is viewing the Semantic Canvas for a PR whose Capability+Flow level is classified
    When the reviewer selects the Capability+Flow stop on the zoom-altitude rail
    Then the camera shows the territory map with flow nodes connecting the territories

  Scenario: Selecting the Architecture stop inside a territory reveals its layer shape
    Given the reviewer has dived into the "crowdness-live" territory, which has Architecture-level content
    When the reviewer selects the Architecture stop on the zoom-altitude rail
    Then the camera reveals "crowdness-live"'s internal layer shape before any individual class appears

  Scenario: Selecting the Pattern+Framework stop shows technique tags with glyph icons
    Given the reviewer has dived into a territory whose "PaymentValidator" component is classified with the "Idempotent recovery" technique
    When the reviewer selects the Pattern+Framework stop on the zoom-altitude rail
    Then the "PaymentValidator" node shows the "Idempotent recovery" technique tag with its glyph icon

  Scenario: A component exposing a REST endpoint shows a method-and-path chip
    Given a component in the current territory exposes the REST endpoint "POST /orders"
    When the reviewer selects the Pattern+Framework stop on the zoom-altitude rail
    Then that component's node shows a REST-endpoint chip reading "POST /orders"

  Scenario: A component calling another bounded context synchronously shows a distinct client-adapter marker
    Given a component in the current territory calls another module's API synchronously via "ManagementDeviceLocationClientAdapter"
    When the reviewer selects the Pattern+Framework stop on the zoom-altitude rail
    Then "ManagementDeviceLocationClientAdapter" is shown as a distinct client-adapter node
    And the client-adapter node is visually distinguished from an event-driven dependency rail

  Scenario: Selecting the Structure stop shows one node per touched file, with test files aggregated
    Given the current territory's Structure level touches 3 production files and 100 test files
    When the reviewer selects the Structure stop on the zoom-altitude rail
    Then the reviewer sees one node per production file
    And the reviewer sees a single "Test suite" node showing the 100 changed test files, instead of 100 individual nodes

  Scenario: Expanding the Test suite node lists the actual test files
    Given the current territory's Structure level shows a "Test suite" node aggregating its test files
    When the reviewer expands the "Test suite" node
    Then the reviewer sees the actual list of changed test files

  Scenario: A config/build file gets a distinct icon from a production file
    Given the current territory's Structure level touches the config file "application.yml" and the production file "OrderService.java"
    When the reviewer selects the Structure stop on the zoom-altitude rail
    Then "application.yml" shows a gear-style config icon
    And "OrderService.java" shows the generic file icon

  Scenario: Diving into a territory lands on its first populated altitude stop
    Given the "crowdness-ui" territory has no Architecture-level content but does have Pattern-level content
    When the reviewer dives into the "crowdness-ui" territory
    Then the camera lands on the Pattern+Framework stop, not a fixed default

  Scenario: Selecting a node zooms the camera in proportionally to that node
    Given the reviewer is viewing the Structure stop showing file-level nodes
    When the reviewer selects a file node
    Then the camera zooms in closer to that file node than it would for a larger concept card

  Scenario: Zoom-in on repeated node selection is capped
    Given the reviewer has already zoomed in on one node
    When the reviewer repeatedly selects further nodes in quick succession
    Then the camera's zoom-in never exceeds the maximum single-selection zoom, however many times the reviewer clicks

  Scenario: The semantic-layers toggle overlays each node's dimension badge
    Given the reviewer is viewing any populated altitude stop
    When the reviewer turns on the "Semantic layers" toggle
    Then every visible node shows a small badge naming which dimension it belongs to

  Scenario: The semantic-layers toggle can be turned back off
    Given the reviewer has turned on the "Semantic layers" toggle
    When the reviewer turns the toggle off
    Then no node shows a dimension badge
