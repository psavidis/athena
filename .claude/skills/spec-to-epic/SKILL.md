---
name: spec-to-epic
description: Read a product spec under docs/specs/ and translate not-yet-ticketed parts of it into epic tickets, using the epic issue template. Runs before plan-feature.
---

# Spec to Epic

## Purpose

Specs under `docs/specs/` come from wherever ideas get incepted — the
user's own writing, brainstorming with other AIs, or with you directly.
They range from a vague product vision to a concrete requirement. This
skill's job is to read one, figure out what in it hasn't been turned into
work yet, and produce **epic** tickets that express the design/intent at
a product level — sized for `plan-feature` to decompose into
implementation tickets, not sized for implementation itself.

## Role boundary

- This is the first step in the pipeline, upstream of everything else:
  `spec-to-epic` → `plan-feature` → `engineer-ticket` (+ `qa-ticket`) →
  `review-pr`.
- Produces epic tickets only. Never produces implementation-sized tickets
  directly, never writes code, never opens a PR.
- Stops once the epic(s) exist and are linked back into the spec. Does
  **not** chain into `plan-feature` automatically — breaking an epic into
  child tickets is a separate, later invocation (by the user or by
  `plan-feature` on request).
- Does not resolve ambiguity in the spec by inventing precision that isn't
  there. Where the spec is genuinely vague, the epic should say so
  explicitly (`### Open Questions`) rather than silently deciding for the
  user.

## Process

1. **Find what's not yet ticketed.**
   - Read the target spec fully.
   - Check for an existing tracking marker (see "Tracking" below) noting
     which sections/themes already have an epic. Skip those.
   - If the spec has no tracking marker yet, treat the whole doc as
     untranslated.

2. **Split into epic-sized chunks.**
   - Don't turn an entire large spec into one epic — that's not
     decomposable by `plan-feature` in one pass. Split by the spec's own
     structure: major sections, numbered headings, or clearly distinct
     capabilities/themes are natural epic boundaries.
   - Don't over-split either — a heading that's one paragraph of
     supporting detail for a neighboring section isn't its own epic.
   - If the spec has an explicit scope-limiting section (e.g. an "MVP
     Scope" section), prefer epics that fit within it over the full
     long-term vision, unless the user asks for the long-term vision to be
     ticketed too.
   - For a spec that reads as one coherent, already-scoped capability
     rather than a whole product, one epic may be correct — don't split
     for the sake of splitting.

3. **Write each epic using `epic_request.md`.**
   - `### Source`: exact file path + section reference.
   - `### Vision`: restate the intent in the spec's own terms. If the
     source is vision-level/ambiguous rather than a concrete requirement,
     say that plainly here — don't paper over vagueness with invented
     specificity.
   - `### Scope Boundary`: what this epic actually commits to, which may
     be narrower than the full vision paragraph above. This is the
     boundary `plan-feature` decomposes within.
   - `### Success Criteria`: observable outcomes, not implementation
     detail.
   - `### Open Questions`: concrete ambiguities that need a human decision
     before or during breakdown. Leave empty if the spec was concrete
     enough not to need it — don't manufacture questions.
   - `### Links`: link back to the spec file/section.
   - Leave `### Child Tickets` and `### Pull Requests` empty —
     `plan-feature` and `engineer-ticket` populate those later.

4. **Create the issue(s).**
   - `gh issue create --template epic_request.md ...` (or `gh api` if you
     need to set fields the template CLI flag doesn't cover).
   - Label `type:epic` comes from the template; don't also add
     `type:feature`/`type:task`.
   - Do not assign the epic to anyone — assignment happens when a human
     or `plan-feature` picks it up.
   - Add each new epic to the project board
     (https://github.com/users/psavidis/projects/5) at Backlog — epics
     aren't "Ready" for engineering the way a decomposed ticket is.

5. **Mark the spec as tracked.**
   - Add a tracking marker in the spec file itself, next to the
     section(s) covered, pointing at the epic issue number(s). Keep it
     minimal and out of the way of the product content itself, e.g.:
     `<!-- epic: #12 -->` directly under the covered heading, or a single
     tracking table near the top of the doc if the spec has many epics
     and inline markers would get noisy. Pick whichever the doc already
     suggests (start an inline-marker convention for a doc with none yet;
     follow the existing convention if a tracking table already exists).
   - Commit this change to the spec file directly — it's docs content,
     not implementation, and doesn't need its own ticket/PR/review cycle.
     A plain commit against `main` is fine
     (`docs: mark <section> tracked as #<N>`).

## Before finishing

Report:

- which spec was read, and what (if anything) was already tracked and
  skipped
- the epic(s) created (number + title + scope boundary in one line each)
- any Open Questions raised that need the user's input before
  `plan-feature` can proceed
- confirmation the spec file was updated with tracking markers
