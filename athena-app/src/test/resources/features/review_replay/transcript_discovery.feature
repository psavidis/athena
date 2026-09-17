Feature: Review Replay transcript discovery
  Athena discovers a transcript already linked in a GitHub PR's
  description, if one exists, so Replay can later enrich the recorded
  session with it (ticket #213). The transcript is treated as an
  external resource — discovery returns metadata only (platform,
  identifier, URL), never the transcript content itself. GitHub only:
  GitLab MR discovery is aspirational until a GitLab RepositoryProvider
  exists as its own capability.

  Scenario: A PR description links a transcript
    Given a PR whose description reads "**Transcript:** [Recording](https://otter.ai/s/abc123)"
    When Athena discovers transcript references for that PR
    Then a transcript reference for "otter.ai" is found
    And its URL is "https://otter.ai/s/abc123"

  Scenario: A PR description with no transcript link
    Given a PR whose description reads "Fixes the login bug."
    When Athena discovers transcript references for that PR
    Then no transcript reference is found

  Scenario: A PR description linking multiple transcripts
    Given a PR whose description reads "**Transcript (part 1):** [Recording](https://otter.ai/s/abc123)\n**Transcript (part 2):** [Recording](https://otter.ai/s/def456)"
    When Athena discovers transcript references for that PR
    Then 2 transcript references are found

  Scenario: A PR with an empty description
    Given a PR whose description is empty
    When Athena discovers transcript references for that PR
    Then no transcript reference is found
