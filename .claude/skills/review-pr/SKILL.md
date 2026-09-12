---
name: review-pr
description: Review an open PR against its linked ticket - post line comments and a summary, approve, then squash-merge, clean up branches, and close the ticket.
---

# Review PR

## Purpose

Take a PR from "in review" through to merged and closed. This is the
"reviewer" role — it owns everything from picking up the review to
closing out the ticket on the board.

## Role boundary

- Review the actual diff against the ticket's acceptance criteria — don't
  rubber-stamp, but don't invent scope the ticket didn't ask for either.
- Do not implement fixes yourself. If something needs to change, comment
  on it; the engineer addresses it and re-requests review.
- Merging is the last step of this skill, not a separate one — except
  under the "approval-gated" autonomy mode below, where this skill stops
  short of merging.

## Reference: project board

Board: https://github.com/users/psavidis/projects/5
Project node ID: `PVT_kwHOBB9O8s4BjQ9M`
Status field ID: `PVTSSF_lAHOBB9O8s4BjQ9MzhiF83Q`
Status options: Backlog `f75ad846` · Ready `61e4505c` · In progress
`47fc9ee4` · In review `df73e18b` · Done `98236657`

```
gh project item-edit --id <ITEM_ID> --project-id PVT_kwHOBB9O8s4BjQ9M \
  --field-id PVTSSF_lAHOBB9O8s4BjQ9MzhiF83Q --single-select-option-id <OPTION_ID>
```

## Process

1. **Claim the review.**
   - Assign the PR (and the linked ticket, if not already assigned to the
     engineer) to yourself as reviewer: `gh pr edit <N> --add-assignee @me`.
   - The ticket's board Status should already be **In review** (set by the
     engineer); leave it as-is while reviewing.

2. **Review the diff.**
   - Read the PR description and the linked ticket (`Related-to: #N`)
     side by side. Check the change actually satisfies the ticket's
     acceptance criteria / functional requirements — not just "does the
     code look fine."
   - Post comments on specific lines where you have a concrete point —
     a bug, a missed edge case, a simplification, a convention violation.
     Use the REST API directly for line comments (each call auto-submits
     as its own `COMMENTED` review — that's fine, it doesn't block
     anything):
     `gh api repos/<owner>/<repo>/pulls/<N>/comments -f body="..." \
       -f commit_id="$(gh pr view <N> --json headRefOid --jq .headRefOid)" \
       -f path="<file>" -F line=<N> -f side="RIGHT"`
     Don't use a single flat top-level comment for line-specific feedback.
   - Do not post a line comment for purely stylistic nitpicks that don't
     affect correctness or clarity, unless they violate an established
     convention.

3. **Summarize for the human.**
   - However many line comments you posted, add one top-level review
     summary comment (`gh pr comment`) that a human can read in a few
     seconds: what you checked, what you found (grouped, not restated
     line by line), and a final verdict line — `**Verdict: APPROVED**` or
     `**Verdict: REQUEST CHANGES**` — since this stands in for the native
     GitHub review state per step 4.
   - If there are zero findings, the summary can be short — say so
     plainly rather than padding it.

4. **Decide: request changes, or approve.**
   - GitHub refuses to let an account approve/request-changes on its own
     PR (`gh pr review --approve` errors with "Can not approve your own
     pull request"). Since engineer and reviewer both act as the same
     authenticated account in this setup, use `gh pr comment` instead of
     `gh pr review` and state the verdict in text — fold this into the
     summary comment in step 3 rather than posting it separately (see the
     verdict line in that comment's template). If a genuinely separate
     reviewer account/bot is ever wired up, switch back to
     `gh pr review --approve` / `--request-changes` for a real GitHub
     review state.
   - If there are correctness-affecting findings, state "Request changes"
     in the summary comment and stop here. Do not merge. Wait for the
     engineer to push updates, then re-review from step 2.
   - If there's nothing blocking, state "Approved" in the summary comment.

5. **Merge — autonomy-dependent.**
   - **Default / fully autonomous mode:** proceed to merge yourself (see
     step 6).
   - **Approval-gated autonomous mode** (user said you can work
     autonomously but merging needs their approval): do not merge. Instead:
     - Add label `status:pending-approval` to the PR and the ticket.
     - Assign both the PR and the ticket to the human user (not yourself).
     - Leave the ticket's board Status at **In review** — it moves to
       **Done** only once the human merges and this skill (or the human)
       completes step 7.
     - Stop here and report that the PR is approved and waiting on the
       user's merge.

6. **Squash-merge.**
   - Merge with `gh pr merge <N> --squash --delete-branch`.
   - The squash commit message: use the PR title as the commit title
     (already Conventional-Commits-formatted from `engineer-ticket`), and
     write a concise commit body capturing the semantic changes — not a
     dump of every intermediate commit message. Base it on the PR's
     High-Level Changes / Why if present, condensed further.
   - `--delete-branch` removes the remote branch. Also delete the local
     branch if it exists in the current checkout:
     `git branch -d <branch-name>` (after switching off it), and
     `git remote prune origin` to clear the stale remote-tracking ref.

7. **Close out the ticket.**
   - Move the ticket's board Status to **Done**.
   - Post a comment on the ticket noting it was merged, referencing the
     PR, and including any concerns/notes carried over from the review
     (e.g. follow-up work you're intentionally not blocking on).
   - If everything is fully resolved with no open follow-ups, close the
     ticket: `gh issue close <N>`. If there are open concerns that need
     someone's attention before it's truly done, say so in the comment and
     leave it open rather than closing prematurely.

## Before finishing

Report:

- PR number and merge commit
- summary of review findings (if any)
- ticket number and its final state (closed, or left open with reason)
- branches deleted
- whether this stopped at approval-gated hand-off, or completed the full
  merge
