# AI Integration

AI is an optional, second-stage assistant that runs after the human has
already formed their own understanding — never a replacement for human
review, never the primary reviewer. It receives the human's Review
Context (intent, what was reviewed, concerns) plus the diff and semantic
model, and answers "given what the human understood, what might they
have missed?" What's included/excluded from what's sent to the provider
must be explicit and reviewer-inspectable before sending (private notes
and unreviewed/generated files are excluded by default). AI never
controls the review workflow, auto-applies anything, or blocks
submission.

Depends on Review Context & Continuity (consumes the Review Context
artifact that capability produces). See Epic #7 for the full vision and
scope boundary.

## Features

- `ai_context_boundary.feature` — what's included/excluded from the AI payload, and why
- `ai_analysis_orchestration.feature` — context boundary → provider → findings board pipeline
- `ai_findings_review.feature` — independently accepting/dismissing each finding
- `post_review_ai_analysis.feature` — AI analysis runs only after human review is complete
