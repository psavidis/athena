# Review Context & Continuity

Everything a human reviewer does while forming an understanding —
comments at multiple scopes, private notes never synced to GitHub —
accumulates into a structured Review Context artifact: the PR's intent,
what was reviewed vs. skipped, concerns, assumptions, and unresolved
questions. This artifact is what later feeds AI Integration and what's
shown back to the reviewer as a summary before submission. This state
must survive new commits landing mid-review: the system tells a reviewer
which previously-reviewed Changes are unchanged, changed, or new, which
requires a Change to have a stable semantic identity across revisions
(shared with the Semantic Change Engine's identity model).

See Epic #6 for the full vision and scope boundary.

## Features

- `review_context_and_submission.feature` — Review Context artifact assembly and pre-submission summary
- `review_continuity_across_revisions.feature` — tracking review state across new commits and force-pushes
