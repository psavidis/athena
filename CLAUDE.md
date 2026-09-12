# Athena — ticket workflow

This project uses a ticket-driven workflow with five roles, each backed by
a skill under `.claude/skills/`:

- **Spec-to-epic** (`spec-to-epic`) — reads a spec under `docs/specs/`
  (anywhere from a vague product vision to a concrete requirement) and
  translates the not-yet-ticketed parts of it into epic tickets
  (`type:epic`, `epic_request.md` template).
- **Planner** (`plan-feature`) — breaks an epic (or a directly-described
  feature) into implementation-sized tickets using
  `.github/ISSUE_TEMPLATE/`.
- **Engineer** (`engineer-ticket`) — claims a ticket, implements it,
  invokes QA, opens a PR.
- **QA** (`qa-ticket`) — writes/runs tests for a ticket's implementation,
  reports pass/fail. Invoked by the engineer inline (no subagent spawn).
- **Reviewer** (`review-pr`) — reviews the PR, approves, squash-merges,
  cleans up branches, closes the ticket.

Pipeline order: `spec-to-epic` → `plan-feature` → `engineer-ticket` (+
`qa-ticket`) → `review-pr`. Each stage stops at its own boundary and does
not auto-chain into the next — a human (or a separate instruction)
triggers each stage.

Read the relevant `SKILL.md` before acting in that role — this file is the
overview, the skills carry the actual step-by-step process and command
reference (board IDs, label names, etc.).

## Lifecycle at a glance

```
(spec) → epic → todo → in-progress → in-review → (pending-approval, gated mode only) → done
```

| State            | Signal                                             |
|------------------|-----------------------------------------------------|
| Todo             | issue open, unassigned                              |
| In progress      | issue open, assigned                                |
| In review        | issue open, assigned, linked PR open                |
| Pending approval | label `status:pending-approval` (gated mode only)   |
| Done             | issue closed                                        |

There is intentionally no `status:in-progress` / `status:review` /
`status:done` label — the assignee and open/closed state already carry
that signal, and a parallel label would just be state that can drift out
of sync. `status:pending-approval` is the one label that exists because
nothing else on the issue can represent that state.

The GitHub Projects board (https://github.com/users/psavidis/projects/5)
is the visual Kanban layer on top of this: Backlog → Ready → In progress →
In review → Done. Each skill moves its own ticket's Status as it transitions
it — see each `SKILL.md` for the exact `gh project item-edit` commands and
field/option IDs.

## PR conventions

- PR title = the eventual squash-merge commit title, Conventional Commits
  style: `type(scope): description`. See
  `.github/PULL_REQUEST_TEMPLATE.md` for the full type table and the rule
  for when to omit `(scope)`.
- PR body fields (Context, Why, High-Level Changes, Testing, Risks /
  Follow-ups) are all optional — include only what earns its place. Every
  PR must include `Related-to: #<ticket>` for a clickable cross-reference,
  but that alone does not attach the PR to the ticket — the engineer must
  also add the PR link to the ticket's own `### Pull Requests` section
  (see `engineer-ticket`).

## Assignment rules

- Whoever (engineer or reviewer) is actively working a ticket/PR assigns
  it to themselves (`@me`) — this is how anyone looking at the board can
  tell who's on what.
- When a ticket/PR needs the human user's action (approval-gated merge,
  or anything the workflow can't resolve on its own), assign it to the
  user instead of leaving it with the agent.

## Autonomy modes

The user sets the mode explicitly per session/instruction:

- **Fully autonomous** — work through existing tickets end to end,
  including merging to master, without stopping for approval.
- **Autonomous, approval-gated merge** — work through tickets end to end,
  but stop right before merging: label the PR/ticket
  `status:pending-approval`, assign both to the user, and stop. The user
  merges it themselves. See `review-pr`'s step 5 for the exact handling.

Without either instruction, default to the normal interactive mode (ask
before merging, as with any other risky/hard-to-reverse action).
