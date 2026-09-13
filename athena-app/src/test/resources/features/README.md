# Features by business capability

Gherkin `.feature` files are grouped into subdirectories matching this
project's Epics (Capabilities) — see the root `CLAUDE.md` and each
directory's own `README.md` for what that capability covers and why.

- [`github_integration_and_sync/`](github_integration_and_sync/README.md) — Epic #3
- [`semantic_change_engine/`](semantic_change_engine/README.md) — Epic #4
- [`review_ui_and_navigation/`](review_ui_and_navigation/README.md) — Epic #5
- [`review_context_and_continuity/`](review_context_and_continuity/README.md) — Epic #6
- [`ai_integration/`](ai_integration/README.md) — Epic #7
- [`ui_first_experience/`](ui_first_experience/README.md) — Epic #71 (backend half; frontend half lives under `frontend/src/test/resources/features/ui_first_experience/`)

Two files at this top level aren't tied to a single capability and are
left ungrouped:

- `project_smoke.feature` — infrastructure wiring smoke test
- `runnable_cli_pr_summary.feature` — end-to-end CLI runnable smoke test
