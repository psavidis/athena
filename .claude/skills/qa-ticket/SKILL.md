---
name: qa-ticket
description: Design and run the tests for a ticket's implementation, and report pass/fail. Invoked by the engineer before opening a PR.
---

# QA Ticket

## Purpose

Verify one ticket's implementation by adding/updating the tests it needs
and running the suite, then reporting the result. This is the "tester"
role in the ticket workflow.

## Role boundary — read this first

- QA never edits implementation code. It only writes/updates test code and
  reports what it finds.
- QA runs once per implementation pass. If tests fail, QA reports the
  failures back and stops — it does not loop, retry, or wait for a fix.
  Fixing the code and re-running the suite is the engineer's job, done
  directly in the same session (do not re-invoke this skill just to
  recheck a fix — re-invoke it only when new test *design* work is needed,
  e.g. new scenarios uncovered by a scope change).
- This runs in the same context as the engineer's work on the ticket — do
  not spawn a subagent for this. It's one inline step, not a separate
  workstream.

## Process

1. Identify what the ticket's acceptance criteria / functional
   requirements actually require to be verified.
2. Detect the project's existing test tooling/conventions (test runner,
   file layout, naming) from the repo — do not assume a framework. If the
   repo has no tests yet, set up the minimal convention the ticket needs
   and follow it consistently for future tickets.
3. Write or update tests covering:
   - the golden path described in the ticket
   - edge cases implied by the acceptance criteria
   - regressions for whatever the ticket fixes, if it's a bug ticket
4. Run the full relevant test suite (not just the new tests) to catch
   regressions elsewhere.
5. Report the result.

## Before finishing

Report:

- test files added/changed
- full command used to run the suite
- pass/fail result, with failure details (file:line, assertion, actual vs
  expected) for anything failing
- any acceptance criteria you could not verify with an automated test, and
  why (e.g. requires manual/browser verification)

Keep the report factual and short — this is input for the engineer to act
on, not a narrative.
