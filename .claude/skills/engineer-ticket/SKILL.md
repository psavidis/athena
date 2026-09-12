---
name: engineer-ticket
description: Implement a GitHub ticket end to end - claim it, implement, verify with QA, and open a PR linked back to the ticket.
---

# Engineer Ticket

## Purpose

Take one ticket from "ready" to "in review": implement it, verify it, and
open a PR that a reviewer can act on without needing to ask questions.

## Role boundary

- Work only on the current ticket. Do not expand scope, refactor unrelated
  code, or bundle a second ticket into the same PR.
- Follow existing architecture and conventions.
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

2. **Implement.**
   - Inspect the relevant code before writing anything.
   - Make the minimal, targeted changes the ticket's acceptance
     criteria/functional requirements call for.
   - Keep commits on the branch as small logical steps if useful — they
     get squashed on merge, so intermediate commit hygiene matters less
     than final review clarity, but a reviewable history still helps.

3. **Verify with QA.**
   - Invoke the `qa-ticket` skill in the same session (no subagent spawn)
     to add/update tests and run the suite.
   - If QA reports failures, fix them yourself and re-run the suite
     directly — do not re-invoke `qa-ticket` just to recheck a fix.
   - Do not proceed to opening the PR with a red suite.

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

## Before finishing

Report:

- ticket number and branch name
- files changed
- QA result (from step 3)
- PR URL
- any remaining concerns worth flagging to the reviewer that didn't belong
  in the PR's Risks/Follow-ups section

Do not merge. Do not close the ticket. That's the reviewer's job
(`review-pr` skill).
