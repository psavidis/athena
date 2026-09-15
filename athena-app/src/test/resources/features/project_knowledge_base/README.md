# Project Knowledge Base

Athena can optionally consult a persistent, human-maintained project
knowledge base as additional contextual evidence during a review (ticket
#118) — architectural decisions, business logic, project conventions, and
other knowledge that cannot be derived from the current diff alone. The
knowledge base is not authoritative: it informs Athena's reasoning, it
never determines Athena's conclusions.

A Knowledge Provider is a configurable, optional integration, alongside
the Pull Request provider (GitHub) in Athena's External Systems /
Integrations configuration area — never a mandatory part of the review
pipeline. Athena must work fully, with no errors or warnings, when no
Knowledge Provider is configured, and a configured provider failing must
never prevent a review from completing.

The initial concrete provider is Obsidian: Athena reads Markdown notes
from a configured vault directory, retrieves only the notes relevant to
the current review (never the whole vault), and can persist a
user-approved "knowledge candidate" — typically an accepted AI finding
worth remembering — back to the vault.

This ticket is scoped to the external, user-owned knowledge base only.
Athena's own autonomously-learned project memory from Git/PR history
(architectural relationships, coupling, recurring patterns) is a
separate, much larger concern — see ticket #121 ("Athena Project Memory &
Knowledge Sources"), which will build on the provider abstraction
introduced here rather than duplicating it.

## Features

- `knowledge_provider_configuration.feature` — configuring/disconnecting
  the Obsidian Knowledge Provider, and rejecting an invalid vault path
- `knowledge_retrieval_during_review.feature` — relevant knowledge
  reaching the AI reasoning stage, irrelevant notes staying out, and
  graceful behavior with no provider configured or a provider failure
- `knowledge_candidate_capture.feature` — saving an accepted finding as a
  new knowledge item, with provenance, and rejecting the save when no
  provider is configured

These live in `athena-app` (rather than `athena-core`) because they
exercise the full web-layer wiring — `KnowledgeConfigController`,
`AiAnalysisController`, and the concrete `ObsidianKnowledgeProvider`,
which reads real files from disk — the same reason
`external_findings_as_evidence.feature` and `eslint_analysis_provider.feature`
(see `semantic_change_engine/README.md`) live here rather than in
`athena-core`.
