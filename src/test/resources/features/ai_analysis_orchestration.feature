Feature: AI analysis orchestration (context boundary -> provider -> findings board)
  Triggering AI analysis composes the context boundary, the configured
  provider, and the findings board in one step, so the boundary's
  exclusions can never be silently bypassed by a caller that forgets to
  use them (epic #7 #63).

  Scenario: Triggering AI analysis returns a findings board ready for review
    Given a PR with a reviewed rename Change
    And the AI provider will flag a possible missed edge case about the rename Change
    When the reviewer triggers AI analysis for the PR
    Then the returned findings board has one item, attributed as "AI", with a jump target to the rename Change

  Scenario: The AI provider never receives private notes, unreviewed Changes, or generated-file Changes
    Given a PR with a reviewed rename Change, a private note on it, an unreviewed move Change, and a reviewed Change confined to generated files
    When the reviewer triggers AI analysis for the PR
    Then the AI provider received no private notes
    And the AI provider's received Changes do not include the move Change
    And the AI provider's received Changes do not include the generated-file Change
    And the AI provider's received Changes include the rename Change
