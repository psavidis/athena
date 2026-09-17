Feature: Review Briefing semantic change summary
  A developer opening a PR gets a one- or two-sentence semantic
  explanation of what changed, drawn from Athena's existing
  seven-dimension classification — not a file/line-count summary (ticket
  #219). Advisory only, the same "not authoritative" framing
  ModuleNarrativeProvider already establishes.

  Scenario: A PR with detected Changes gets a change summary
    Given a PR with a detected Change classified as "RESPONSIBILITY"
    When Athena generates the Review Briefing's change summary
    Then the briefing's change summary is present

  Scenario: A PR with no detected Changes has no change summary
    Given a PR with no detected Changes
    When Athena generates the Review Briefing's change summary
    Then the briefing's change summary is absent

  Scenario: The summary is generated from the PR's Changes, not guessed
    Given a PR with a detected Change classified as "RESPONSIBILITY"
    When Athena generates the Review Briefing's change summary
    Then the summary provider was asked about that PR's own Changes
