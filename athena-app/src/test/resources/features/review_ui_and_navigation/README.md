# Review UI & Navigation

The reviewer's primary navigation structure is Changes, not files — a
Change Map replaces the file tree as the default entry point, with the
file tree available as a fallback rather than the primary mode. Before
inspecting individual Changes, the reviewer sees a high-level PR
Understanding View establishing a mental model, then a Review Queue
guiding (not dictating) order. The UI lets a reviewer move fluidly
between abstraction levels (Intent → Change → Symbol → File → Diff →
Line) in both directions. Review completion is always an explicit human
action, never automatic just because every Change has been classified.

See Epic #5 for the full vision and scope boundary.

## Features

- `change_map_view.feature` — the Change Map as the default navigation entry point
- `pr_understanding_and_review_queue.feature` — the PR Understanding View and Review Queue
- `drill_down_navigation.feature` — multi-level drill-down (Change → Symbol → File → Diff → Line)
- `comments_and_private_notes.feature` — comments/private notes at line, symbol, Change, and review scope
- `review_state_and_completion.feature` — per-Change review state display and explicit review completion
- `search_and_filtering.feature` — search and filtering across Changes/files/symbols/comments (MVP subset)
