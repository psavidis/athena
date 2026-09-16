Feature: Importing a shared review artifact from another Athena instance
  A developer imports a Review Recording artifact (ticket #207) a
  teammate recorded on their own machine and shared as a file, so review
  knowledge isn't siloed to whoever ran the session (ticket #217).
  File-based/manual sharing only — no centralized Athena service.

  Scenario: A developer imports a teammate's shared artifact
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" inspects the "OrderService" entity
    And "Petros" has tagged the current moment as a question
    And "Petros" has confirmed that moment
    And "Petros" has stopped the recording
    And the recording's artifact has been exported to a shared file
    When a developer imports that shared file into a project selected on "acme/widgets"
    Then the import succeeds
    And a developer can open the imported artifact as a Replay

  Scenario: Importing an artifact for a different repository is rejected
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" has stopped the recording
    And the recording's artifact has been exported to a shared file
    When a developer imports that shared file into a project selected on "other-org/other-repo"
    Then the import is rejected as invalid

  Scenario: Importing a malformed file is rejected
    When a developer imports a malformed shared file into a project selected on "acme/widgets"
    Then the import is rejected as invalid

  Scenario: Importing an artifact whose recording id already exists locally is rejected
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" has stopped the recording
    And the recording's artifact has been exported to a shared file
    And that same artifact is already stored locally
    When a developer imports that shared file into a project selected on "acme/widgets"
    Then the import is rejected as invalid
