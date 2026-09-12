---
name: plan-feature
description: Break a feature down into GitHub tickets (issues) sized for individual engineering work, using the repo's issue templates.
---

# Plan Feature

## Purpose

Turn a feature request (from the user, a spec, or a design doc) into a set
of GitHub issues small enough for one engineer to pick up and finish in a
single PR each.

## Role boundary

- This skill only creates/edits tickets. It never writes implementation
  code and never opens a PR.
- It does not implement, review, or merge anything.

## Process

1. Understand the feature: read whatever spec/context exists (e.g.
   `docs/specs/`), and clarify scope with the user if it's ambiguous.
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

Do not start implementation. Do not assign tickets to anyone — assignment
happens when an engineer picks up the work (see the `engineer-ticket`
skill).
