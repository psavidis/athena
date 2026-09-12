---
name: plan-feature
description: Break a feature down into GitHub tickets (issues) sized for individual engineering work, using the repo's issue templates.
---

# Plan Feature

## Purpose

Turn a feature request into a set of GitHub issues small enough for one
engineer to pick up and finish in a single PR each. The feature request
is usually an **epic ticket** produced by `spec-to-epic` (its `### Scope
Boundary` and `### Success Criteria` are the input), but can also be a
feature described directly by the user without going through an epic
first.

## Role boundary

- This skill only creates/edits tickets. It never writes implementation
  code and never opens a PR.
- It does not implement, review, or merge anything.
- If the input is an epic, decompose within its `### Scope Boundary` —
  don't silently pull in the fuller `### Vision` if it's broader than the
  boundary the epic committed to. If the epic has unresolved
  `### Open Questions`, resolve them with the user before decomposing,
  don't guess.

## Process

1. Understand the feature: if working from an epic, read the epic issue
   in full (Vision, Scope Boundary, Success Criteria, Open Questions); if
   working from a raw request, read whatever spec/context exists (e.g.
   `docs/specs/`). Clarify scope with the user if it's ambiguous.
2. Break the feature into the smallest independently-shippable pieces of
   work. Prefer several small tickets over one large one — each ticket
   should be completable in one PR.
3. For each ticket, pick the right issue template
   (`.github/ISSUE_TEMPLATE/`):
   - `feature_request.md` for a user-facing capability
   - `task.md` for supporting work that is neither a feature nor a bug
     (scaffolding, refactors, config, CI, etc.)
   - `bug_report.md` only if the "feature" work is actually fixing broken
     behavior
4. Fill in the template's sections properly — do not leave a ticket as a
   bare title. In particular:
   - `feature_request.md`: write a real User Story, concrete Functional
     Requirements, and call out Limitations of Scope so the ticket doesn't
     silently grow.
   - `task.md`: write concrete Acceptance Criteria — a task ticket without
     acceptance criteria is not actionable.
5. If one ticket depends on another (must merge first), say so under
   `### Links` or `### Hints` in the ticket body, referencing the other
   ticket by number once it exists.
6. Create the issues with `gh issue create` using the appropriate
   `--template`, or via `gh api` if you need to set fields the template
   doesn't cover.
7. Add each created issue to the project board and leave its Status at the
   board's initial "not started" column (e.g. Backlog/Ready) — this skill
   never advances a ticket past that point.
8. If decomposing an epic, append each child ticket to the epic's
   `### Child Tickets` section (`gh issue edit <epic-N> --body-file ...`)
   — this doesn't happen automatically from mentioning the epic number in
   a child ticket's `### Links`, the same way a PR doesn't auto-attach to
   its ticket (see `engineer-ticket` for the equivalent PR-side step).

## Sizing guidance

- A ticket should be reviewable in one sitting. If describing the change
  requires "and then" more than twice, split it.
- Don't create a ticket for work with no clear Definition of Done — either
  scope it down until there is one, or fold it into Limitations of Scope on
  a related ticket.

## Before finishing

Report to the user:

- the list of tickets created (number + title)
- how they relate to each other (sequencing/dependencies)
- anything you deliberately left out of scope

Do not start implementation. Do not write Use Cases or Gherkin scenarios
— that's `spec-writer`'s job, next in the pipeline. Do not assign tickets
to anyone — assignment happens when `spec-writer`/`engineer-ticket` picks
up the work.
