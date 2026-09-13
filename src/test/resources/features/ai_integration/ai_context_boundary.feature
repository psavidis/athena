Feature: AI context boundary (included/excluded data)
  Before any data is sent to an AI provider, the reviewer can see exactly
  what will be included and excluded. Private notes, Changes the reviewer
  hasn't reviewed yet, and Changes touching only generated files are
  excluded by default and never reach the actual request (epic #7 §36).

  Scenario: The reviewer can see what's included before sending
    Given the reviewer has assembled an AI context boundary for a PR with a reviewed rename Change and a mechanical replacement Change marked mechanical
    Then the payload sent to the AI provider includes the rename Change
    And the payload sent to the AI provider includes the mechanical replacement Change

  Scenario: A Change the reviewer hasn't reviewed yet is excluded from what's sent
    Given the reviewer has assembled an AI context boundary for a PR with a reviewed rename Change and an unreviewed move Change
    Then the boundary's excluded unreviewed Changes include the move Change
    And the payload sent to the AI provider does not include the move Change

  Scenario: A Change touching only generated files is excluded from what's sent, even if reviewed
    Given the reviewer has assembled an AI context boundary for a PR with a reviewed rename Change and a reviewed Change confined to generated files
    Then the boundary's excluded generated Changes include the generated-file Change
    And the payload sent to the AI provider does not include the generated-file Change
    And the payload sent to the AI provider includes the rename Change

  Scenario: Private notes are excluded from what's sent, and the boundary reports this
    Given the reviewer has assembled an AI context boundary for a PR with a reviewed rename Change and a private note attached to it
    Then the boundary reports private notes as excluded
    And the payload sent to the AI provider has no private notes
