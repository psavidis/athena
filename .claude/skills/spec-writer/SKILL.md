---
name: spec-writer
description: Turn a feature ticket's intent into Use Cases and committed Gherkin scenarios, before any test or code is written. Runs between plan-feature and qa-ticket.
---

# Spec Writer

## Purpose

A feature ticket (from `plan-feature`) describes *what* should be built in
prose. This skill turns that into concrete, black-box-observable **Use
Cases**, each expressed as one or more **Gherkin scenarios** committed to
the repo as `.feature` files. These scenarios are the executable
specification everything downstream is built against — `qa-ticket` writes
step definitions and tests against them, `engineer-ticket` implements
until they pass. Nothing about internals belongs here.

## Where this sits in the pipeline

```
spec-to-epic → plan-feature → spec-writer → qa-ticket → engineer-ticket → review-pr
 (Capability)    (Feature)    (Use Cases/    (tests,     (implementation
                                Gherkin)       written red)  to green)
```

`spec-to-epic`'s epics are the product's business capabilities (DDD
sense) — GitHub Integration, Semantic Change Engine, and so on. Each
Epic's `plan-feature` decomposition produces Feature tickets. This skill
is the layer between a Feature ticket and any test/implementation work.

## Role boundary

- Writes Use Cases and `.feature` files only. Never writes step
  definitions, unit tests, or production code — that's `qa-ticket` and
  `engineer-ticket`.
- Scenarios must be black-box: expressed in terms of observable inputs,
  actions, and outcomes a user/caller of the system would recognize —
  never in terms of internal classes, methods, or implementation
  mechanics. If a scenario can't be phrased without naming an internal
  component, the use case isn't concrete enough yet — go back to the
  ticket, not into the implementation.
- Runs inline in the same session as whichever role invoked it (no
  subagent spawn) — this is a focused authoring step, not a workstream of
  its own.

## Process

1. **Extract Use Cases from the ticket.**
   - Read the feature ticket's User Story / Functional Requirements /
     Acceptance Criteria in full.
   - A Use Case is one discrete, nameable thing a user or external caller
     does with the system and the outcome they get — not an
     implementation task. ("A reviewer marks a Change as reviewed" is a
     use case; "add a `reviewed` boolean to the Change model" is not.)
   - List every use case the ticket implies, including the edge cases and
     failure modes its Acceptance Criteria call for, not just the golden
     path.

2. **Add a `### Use Cases` section to the ticket.**
   - Append it to the feature ticket body (`gh issue edit`), listing each
     use case in one line of plain language before its Gherkin form. This
     keeps the ticket readable for a human skimming it without opening
     the `.feature` file.

3. **Write Gherkin scenarios for each use case.**
   - One `.feature` file per use case (or per closely-related group of
     use cases within the same capability), using `Given/When/Then` in
     terms a domain expert would recognize — not test/implementation
     jargon.
   - Cover the golden path and the meaningful edge cases/failure modes
     from the ticket's acceptance criteria — don't write only the happy
     path.
   - Detect the repo's language/tooling before deciding the Gherkin
     runner and file layout (e.g. Cucumber-JVM under
     `src/test/resources/features/` for a Java project) — don't assume;
     if this is the first `.feature` file in the repo, establish the
     convention and follow it consistently afterward.

4. **Commit the `.feature` files** to the ticket's branch (create the
   branch here if `engineer-ticket` hasn't already — whichever role runs
   first for this ticket creates it) so `qa-ticket` and `engineer-ticket`
   build on committed scenarios, not scenarios living only in chat.

## Before finishing

Report:

- the use cases identified (one line each)
- the `.feature` file(s) written, and the path/convention used
- anything in the ticket too vague to turn into a concrete scenario —
  flag it back to `plan-feature`/the ticket rather than guessing
