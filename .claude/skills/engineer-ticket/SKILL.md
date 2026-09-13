---
name: engineer-ticket
description: Implement a GitHub ticket end to end - claim it, ensure Gherkin/tests exist and are red, implement until green, and open a PR linked back to the ticket.
---

# Engineer Ticket

## Purpose

Take one feature ticket from "ready" to "in review": ensure it has
committed Gherkin scenarios and tests (delegating to `spec-writer` /
`qa-ticket` if they haven't run yet), implement against those tests until
green, and open a PR that a reviewer can act on without needing to ask
questions.

## Where this sits in the pipeline

```
spec-to-epic → plan-feature → spec-writer → qa-ticket → engineer-ticket → review-pr
                                (Gherkin)     (tests,      (implementation
                                               written red)  to green)
```

This is outside-in TDD: the tests define the implementation, not the
other way around. Do not write production code before a failing test
exists for it.

## Role boundary

- Work only on the current ticket. Do not expand scope, refactor unrelated
  code, or bundle a second ticket into the same PR.
- Follow existing architecture and conventions, and follow
  `CODE_STYLE.md` (repo root) — the sole style guide for this codebase.
  It covers design (immutability, nullability, exception strategy,
  nesting depth, object-creation patterns, DTO categorization) and test
  design. There is no second style guide and no auto-formatter in the
  build — for pure mechanics it doesn't address, use ordinary Java
  convention and judgment, not a rigid tool.
- Never make an implementation choice to satisfy a whitebox
  mock/expectation instead of real behavior — if a test's mocking looks
  unjustified per `qa-ticket`'s Detroit-school rules, fix the test's
  approach (or flag it), don't code around it.
- This skill does not review or merge — it stops once the PR is open and
  linked.

## Reference: project board

Board: https://github.com/users/psavidis/projects/5
Project node ID: `PVT_kwHOBB9O8s4BjQ9M`
Status field ID: `PVTSSF_lAHOBB9O8s4BjQ9MzhiF83Q`
Status options: Backlog `f75ad846` · Ready `61e4505c` · In progress
`47fc9ee4` · In review `df73e18b` · Done `98236657`

To move an item, first find its project item ID (from
`gh project item-list 5 --owner psavidis --format json`, matching on the
issue/PR number), then:

```
gh project item-edit --id <ITEM_ID> --project-id PVT_kwHOBB9O8s4BjQ9M \
  --field-id PVTSSF_lAHOBB9O8s4BjQ9MzhiF83Q --single-select-option-id <OPTION_ID>
```

If a ticket isn't on the board yet, add it first:
`gh project item-add 5 --owner psavidis --url <issue-url>`.

## Process

1. **Claim the ticket.**
   - Read the ticket in full, including any plan/design linked under
     `### Links` or `### Hints`.
   - Assign it to yourself: `gh issue edit <N> --add-assignee @me`.
   - Move its board Status to **In progress**.
   - Create a branch for the work (naming: short, kebab-case, ideally
     including the ticket number, e.g. `142-refresh-token-before-expiry`).

2. **Ensure Gherkin and tests exist.**
   - Check whether this ticket already has committed `.feature` files and
     a `### Use Cases` section. If not, this ticket isn't ready for
     autonomous work — `spec-writer` is a ticket-creation role the user
     runs manually on demand (see CLAUDE.md's "Ticket-creation vs.
     autonomous-work boundary"), not something to invoke here. Stop and
     report that the ticket lacks committed Gherkin; do not invoke
     `spec-writer` yourself.
   - Check whether step definitions/tests already exist for those
     scenarios. If not, invoke `qa-ticket` inline next (this is test
     authoring against already-committed Gherkin, not ticket creation, so
     it's in scope here). Expect the suite to be red at this point —
     that's the correct starting state, not a problem to fix before
     implementing.
   - If both already exist (e.g. re-entering this ticket after an earlier
     session), skip straight to implementing.

3. **Implement to green.**
   - Inspect the relevant code before writing anything.
   - Make the minimal, targeted changes needed to turn the ticket's tests
     green — don't implement beyond what the tests + acceptance criteria
     actually require.
   - Run the suite yourself directly as you go; do not re-invoke
     `qa-ticket` to check progress — that skill is for test *design*, not
     for rechecking a fix. Re-invoke it only if implementation reveals a
     use case the tests don't cover yet.
   - Do not proceed to opening the PR with a red suite (except
     pre-existing, unrelated failures already flagged by `qa-ticket`).
   - Keep commits on the branch as small logical steps if useful — they
     get squashed on merge, so intermediate commit hygiene matters less
     than final review clarity, but a reviewable history still helps.

4. **Open the PR.**
   - Push the branch and open the PR with `gh pr create`. The repo's
     `.github/PULL_REQUEST_TEMPLATE.md` will prefill the body — fill in
     only the fields that earn their place (see the template's own
     guidance comment); do not pad the description with empty sections.
   - Title must follow Conventional Commits (`type(scope): description`,
     scope omitted when it would be redundant or there's no single owner)
     — see the template header for the type table and scope rules. This
     title is what becomes the squash-merge commit title, so get it right
     here.
   - Body must include `Related-to: #<ticket-number>` — this makes the PR
     number a clickable cross-reference back to the ticket. It does
     **not** auto-populate GitHub's Development sidebar or the board's
     "Linked pull requests" field (only close-keywords like `Closes #N`
     do that, and this workflow doesn't want auto-close on merge).
   - Assign the PR to yourself.
   - Append the PR's URL to the ticket's own `### Pull Requests` section
     in the issue body (`gh issue edit <N> --body-file <updated-body>`)
     — this is the actual "attach the PR to the ticket" step; it does not
     happen automatically from `Related-to:` alone.

5. **Hand off for review.**
   - Move the ticket's board Status to **In review**.
   - Do not set a review label — the linked, open PR *is* the "in review"
     signal on the ticket itself; the board Status is the visible tracker.

6. **Continue into review, inline.**
   - Per CLAUDE.md's autonomy modes, if the session is in **fully
     autonomous** or **approval-gated** mode, invoke `review-pr` inline
     now (no subagent spawn — forking this produced unreliable/empty
     results) on the PR just opened, in the same session. `review-pr`
     owns its own approval-gated stop condition (label + assign to user
     without merging); this skill doesn't need to duplicate that check —
     just hand off.
   - In default/interactive mode (no autonomy instruction given), stop
     here instead and report — the user reviews manually or asks for
     `review-pr` separately.

## Before finishing

Report:

- ticket number and branch name
- files changed
- test result (from step 2/3 — what was red at the start, confirmation
  it's green now)
- PR URL
- if step 6 ran `review-pr` inline: its outcome too (merged/closed, or
  approval-gated hand-off) — otherwise, note that review is the next
  manual step

This skill itself does not merge or close the ticket directly — that
happens via `review-pr` (step 6 above when autonomy allows it, otherwise
invoked separately later).
