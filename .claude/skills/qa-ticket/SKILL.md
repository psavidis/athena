---
name: qa-ticket
description: Turn a feature ticket's committed Gherkin scenarios into step definitions and Detroit-school tests, run them (red is expected pre-implementation), and report. Invoked by the engineer before implementing.
---

# QA Ticket

## Purpose

Turn the `.feature` files `spec-writer` committed for this ticket into
executable tests: step definitions wired to the Gherkin scenarios, plus
whatever supporting unit tests round out coverage the scenarios don't
reach directly. Run them and report the result. This is the "tester"
role in the ticket workflow, and it runs **before** implementation, not
after — these tests are meant to fail first (red), then `engineer-ticket`
implements until they pass (green). That's the point: the tests define
the implementation, not the other way around.

## Where this sits in the pipeline

```
spec-to-epic → plan-feature → spec-writer → qa-ticket → engineer-ticket → review-pr
                                (Gherkin)     (tests,      (implementation
                                               written red)  to green)
```

## Testing philosophy — read this first

- **Detroit school (classicist) by default: black-box, real collaborators,
  state-based verification.** Test through the public API/behavior the
  Gherkin scenario describes. Use real implementations of everything in
  this codebase — do not mock/stub a collaborator just because it's
  "another class." Verify outcomes (return values, resulting state,
  observable side effects), not which internal methods got called.
- **Whitebox/mockist is the exception, not a style choice** — justified
  only at a genuine external system boundary the test can't or shouldn't
  cross for real: a network call to an external API (e.g. GitHub), a
  non-deterministic clock/random source, or expensive/unavailable I/O.
  If you find yourself mocking something that's just an internal class in
  this repo, stop — that's a sign the test is reaching for isolation it
  doesn't need, or that the design needs a real seam (e.g. inject a fake
  clock, not a mocked collaborator).
- Every whitebox mock/stub used must have a one-line comment at the mock
  site naming which boundary justifies it. No comment, no mock.
- `CODE_STYLE.md` (repo root) Section F (Test Design) governs test
  structure in detail — AssertJ over JUnit assertions, domain assertion
  objects, fluent builders/factories for setup, no loops in assertions,
  no build-lifecycle test configuration. Follow it.

## Role boundary

- QA never edits implementation/production code. It writes/updates test
  code (step definitions + supporting tests) only, and reports what it
  finds.
- QA runs once per ticket's test-design pass. If the suite is red (the
  normal, expected state before `engineer-ticket` has implemented
  anything), that is not a failure to fix — report it as the starting
  state and stop. Re-invoke this skill only when new test *design* work
  is needed (new scenarios from a scope change), never to "check if it's
  green now" — that check belongs to `engineer-ticket` re-running the
  suite directly.
- Runs inline in the same session as the engineer's work on the ticket —
  do not spawn a subagent for this.

## Process

1. Read the ticket's `.feature` file(s) written by `spec-writer` and its
   `### Use Cases` section.
2. Detect the project's existing test tooling/conventions (test runner,
   Gherkin/Cucumber setup, file layout, naming) from the repo — do not
   assume. If this is the first ticket with tests, establish the
   convention and follow it consistently afterward.
3. Write step definitions implementing each Gherkin step, calling into
   the public API/entry point the use case describes — not into
   not-yet-existing internals invented for convenience. It is expected
   and correct for these to not compile or fail until `engineer-ticket`
   builds the corresponding implementation.
4. Add supporting unit tests for anything the Gherkin scenarios don't
   reach directly but the ticket's acceptance criteria still require
   (e.g. a pure-function edge case awkward to phrase as a user-facing
   scenario) — same Detroit-school rules apply.
5. Run the full relevant test suite (not just this ticket's new tests) to
   see the actual starting state — some failures are pre-existing/
   unrelated, some are this ticket's tests correctly failing pre-
   implementation. Distinguish the two in the report.
6. Report the result.

## Before finishing

Report:

- test/step-definition files added or changed
- full command used to run the suite
- results, split into: (a) this ticket's new tests — expected to fail
  pre-implementation, list them; (b) anything else failing that isn't
  this ticket's concern — flag as pre-existing/unrelated
- any whitebox mock/stub used, and which boundary justified it
- any acceptance criteria you could not express as an automated test, and
  why

Keep the report factual and short — this is input for the engineer to
implement against, not a narrative.
