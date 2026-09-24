# Athena PR evaluation: baseline for the corpus expansion (16 new entries)

| | |
|---|---|
| Date | 2026-09-24 |
| Athena version | `c6a1721` (same engine as `e2b2780`; only docs changed in between) |
| Scope | The 16 entries added in #311: 11 merged PRs from 7 repositories (5 of them new to the corpus) and 5 `closed-unmerged` PRs |
| Snapshots | `evaluation/snapshots/c6a1721/`, all 26 entries (the original 10 are unchanged from `e2b2780`) |
| Protocol | `evaluation/PROTOCOL.md`, including the new `closed-unmerged` category |
| Evaluator | Claude (agent) |

> **Why this report matters.** The previous batch (#285–#296) was tuned against the original 10
> entries, where Athena reached the diff's total score (85/85). These 16 entries are
> **out-of-sample**. Nothing in Athena was built with them in mind. So they measure how well
> the improvements generalize.

## Executive summary

All 16 new entries ran `READY` in 3–21 s each; the whole 26-entry corpus ran in about 4 minutes.
Every closed PR's head commit was still fetchable, including gson-3054, whose fork was deleted.

| Measure | Original 10 (in-sample, `e2b2780`) | New 11 merged | New 5 closed-unmerged |
|---|---|---|---|
| Athena score / diff score (What + Why + Impact) | 85 / 85 (100 %) | **66 / 83 (80 %)** | **28 / 42 (67 %)** |
| PRs where Athena ≥ the diff | 5 of 10 | 4 of 11 | 0 of 5 |
| Median production-Java coverage | 1.00 | 0.83 | 1.00 |
| Worst production-Java coverage | 0.80 | **0.01** (guava-8651) | 1.00 |

**The headline: the gains generalize for *what changed*, not for everything.**
- **Held up out-of-sample:** test separation, production-first focus areas, `switch`-aware control flow, body summaries and module detection.
  - netty-17594, guava-8647 and hibernate-orm-13477 all read better than their diffs.
  - The behavioral fixes (commons-io-860, guava-8663, netty-17589) are named on the right method.
- **Five gaps no earlier entry could reveal** (details in "New findings"):
  - **F1: Modifier and visibility changes are invisible.** guava-8651 makes 148 files' classes and members `final` (907 changed lines) and narrows some visibilities. Athena reports **1 Change** and lists 146 files as "no semantic change detected". That's actively misleading.
  - **F2: A field's initializer isn't compared.** logback-1051's key line inserts `HALF_DAY` into the constant `VALID_ORDERED_LIST`. The file reads "no semantic change detected".
  - **F3: Framework annotations are matched by substring and counted on test code.** hibernate-orm-13477 gets 133 FRAMEWORK cards: 117 "JPA: Entity Relationship" on test entities, and 4 "Spring: @Service" because `@ServiceRegistry` contains `@Service`.
  - **F4: Custom-named Gradle build files are now in 3 of 13 repositories.** hibernate-orm (`hibernate-core.gradle`), junit5 (`junit-jupiter-api.gradle.kts`) and spring-framework. junit5-6057, the *cross-module* entry, collapses into one territory.
  - **F5: Same-named modules collide.** guava's `guava/` and `android/guava/` are both called `guava`, so guava-8647 shows two separate territories both named `guava`.

**Closed-unmerged: can Athena show why a PR wasn't accepted?** Mostly not, and that's expected.
- **Visible in 1 of 5 entries (gson-3054).** Next to its merged successor gson-3086, Athena shows the rejected version adds a nested class, three fields and a constructor (9 Changes, against 1). "More machinery than needed" is literally visible.
- **Hinted in 2 of 5 entries.** commons-io-866 flags `BufferedFileChannelInputStream#close`, where the leak is, but says nothing about the leak. commons-lang-1790 was closed for inactivity; its approach differs visibly from the merged fix ("branch added" against "branch removed, loop added").
- **Invisible in 2 of 5 entries.** mockito-3843 (losing package-private mocking) and jackson-databind-6163 (a probable de-optimisation). Worse, Athena labels jackson's pure loop unrolling as BEHAVIORAL "branch added, condition changed".

The reasons PRs actually get rejected (resource leaks, performance, design trade-offs) are
consequences, not structure. They sit beyond what the deterministic engine models. They're the
strongest argument so far for an evidence-grounded AI layer on top of the Semantic Change Model.

## Metrics

| Entry | Category | Files (prod Java) | Changes (test) | Cards | Prod-Java coverage | Represented | Territories (+idle) | Time |
|---|---|---|---|---|---|---|---|---|
| commons-io-860 | small-bug-fix | 2 (1) | 4 (3) | 3 | 1.00 | 2/2 | 1 | 3 s |
| logback-1051 | simple-feature | 3 (2) | 7 (4) | 5 | **0.50** | 2/3 | 1 | 3 s |
| guava-8663 | refactoring | 4 (4) | 2 (1) | 3 | 1.00 | 4/4 | 2 (+3) | 6 s |
| netty-17594 | api-change | 4 (3) | 24 (5) | 23 | 0.67¹ | 3/4 | 1 (+9) | 8 s |
| junit5-6057 | cross-module | 13 (7) | 51 (32) | 28 | 0.71¹ | 11/14 | **1** (+1) | 5 s |
| commons-io-872 | dependency-framework | 8 (5) | 34 (14) | 23 | 0.80¹ | 7/8 | 1 | 4 s |
| guava-8647 | architectural | 82 (82) | 235 (5) | 79 | 0.95 | 78/82 | 4 (+3), two named `guava` | 9 s |
| hibernate-orm-13477 | large | 34 (18) | 365 (286) | **218** | 0.83¹ | 28/34 | **1** | 21 s |
| guava-8651 | mixed-noisy | 148 (148) | **1** (1) | 1 | **0.01** | 2/148 | 1 (+3) | 12 s |
| netty-17589 | complex-behavioral | 2 (1) | 2 (1) | 3 | 1.00 | 2/2 | 1 (+1) | 7 s |
| gson-3086 | refactoring | 1 (1) | 1 (0) | 2 | 1.00 | 1/1 | 1 | 3 s |
| commons-lang-1790 | closed-unmerged | 2 (1) | 2 (1) | 3 | 1.00 | 2/2 | 1 | 4 s |
| commons-io-866 | closed-unmerged | 6 (3) | 26 (11) | 17 | 1.00 | 6/6 | 1 | 4 s |
| mockito-3843 | closed-unmerged | 7 (5) | 9 (3) | 9 | 1.00 | 6/7 | 2 (+0) | 4 s |
| jackson-databind-6163 | closed-unmerged | 1 (1) | 1 (0) | 2 | 1.00 | 1/1 | 1 | 5 s |
| gson-3054 | closed-unmerged | 1 (1) | 9 (0) | 11 | 1.00 | 1/1 | 1 | 3 s |

¹ The files that aren't represented are Javadoc-only edits (netty `Http2Connection`, junit5
`Assertions`/`Timeout`, hibernate `Filter`/`FilterDef`/`SQLRestriction`, commons-io `Buffers`),
which is correct. logback's and guava-8651's are real misses (F1, F2).

## Scores (What + Why + Impact, 0–12 each side)

| Entry | Diff | Athena | vs. diff | Notes |
|---|---|---|---|---|
| commons-io-860 | 10 | 6 | worse | The right method is flagged behavioral; the three test names carry the *why*. |
| logback-1051 | 10 | 5 | worse | Three `RollingCalendar` "branch added" entries; the enabling list change is missed (F2). |
| guava-8663 | 9 | 7 | worse | "Branch removed, loop added" on `MultiReader#read`; the `android/` mirror folds into the same Change. |
| netty-17594 | 8 | **9** | **better** | New `Http2ConnectionPropertyKeys` with its global key, plus three behavioral property accessors (the type dispatch). Clearer than 219 diff lines. |
| junit5-6057 | 7 | 7 | equal | `DurationUtils`, `assertTimeout` "condition changed" with `+isPositiveAndRepresentableInNanos`, and 32 test changes grouped. Cross-module shape lost (F4). |
| commons-io-872 | 7 | 6 | within 1 | The API reshaping is visible (`INSTANCE -> CLEANER`, `setClean` builders); the optional `sun.*` access isn't named. |
| guava-8647 | 5 | **7** | **better** | "Remove class ViewCachingAbstractMap" plus "branch removed" on every `keySet`/`values`/`entrySet` reads as "lazy view caching removed". Duplicate `guava` territories (F5). |
| hibernate-orm-13477 | 4 | **7** | **better** | The new `ToOneVisibilityLoader`/`ToOneRestrictions`, binder methods and behavioral loader changes stand out of 2.6k lines, under 133 framework false positives (F3). |
| guava-8651 | 5 | **1** | worse | Almost nothing is shown, and "no semantic change detected" on 146 files is wrong (F1). |
| netty-17589 | 8 | 7 | within 1 | `doStartThread` "branch added" plus a test named for the race. |
| gson-3086 | 10 | 4 | worse | One behavioral "branch added" on `createBoundField`, which is the new guard and correct. The `knownAccessible` field it reads lives in an anonymous class and isn't shown (F7). |
| **Merged total** | **83** | **66** | | |
| commons-lang-1790 | 10 | 6 | worse | Rejection reason: *hinted* (the approach differs from 1794's; the close was administrative). |
| commons-io-866 | 7 | 6 | within 1 | Rejection reason: *hinted* (the leaking `close` path is flagged, not the leak). |
| mockito-3843 | 8 | 5 | worse | Rejection reason: *invisible*. The focus areas are the OSGi fixture classes. |
| jackson-databind-6163 | 8 | 3 | worse | Rejection reason: *invisible*, and loop unrolling is mislabelled BEHAVIORAL. |
| gson-3054 | 9 | 8 | within 1 | Rejection reason: *visible* next to gson-3086 (9 Changes and a nested class, against 1). |
| **Closed total** | **42** | **28** | | |

## New findings

- **F1: Modifier and visibility changes aren't modelled.** Adding `final`, narrowing
  `protected` → package-private or `public` → `private`, and adding or removing `static` or
  `abstract` produce no transformation when the signature and body are otherwise unchanged.
  Visibility is an API change. `final` on a class or method is a subclassing contract.
  (guava-8651; also parts of guava-8647.)
- **F2: Field initializers aren't compared.** A changed initializer on an otherwise-unchanged
  field (here a `static final` array's contents) is invisible, even when it's the whole feature.
  (logback-1051.)
- **F3: Framework classification is noisy outside Spring applications.**
  - Annotation names are matched as substrings (`@ServiceRegistry` → "Spring: @Service").
  - Framework cards are produced for test code, which #285 excluded only from Responsibility.
  - Library repositories that *implement* JPA (hibernate) get a card per test entity.
- **F4: Custom-named Gradle build files (was N7) affect 3 of 13 repositories.** hibernate-orm,
  junit5 and spring-framework all name module build files after the module
  (`<module>.gradle[.kts]`), so their modules aren't found. It's the top architecture-view gap
  in the expanded corpus.
- **F5: Module names must be unique.** Two directories with the same last segment
  (`guava/`, `android/guava/`) become two territories with the same name, so a module-level
  profile lookup by name is ambiguous.
- **F6: Restructured loops read as behavioral.** jackson-databind-6163's 4× unrolling changes
  loop and branch counts without changing behavior, and is flagged BEHAVIORAL. It's the same
  class of false positive #289 fixed for if → switch.
- **F7: Anonymous-class members aren't collected.** gson-3086 adds a `knownAccessible` field to
  an anonymous `BoundField` and guards the reflective checks with it. Athena reports the guard
  (a "branch added" on the enclosing `createBoundField`) but not the cached state it reads.
  *(Corrected after publication: an earlier version of this finding described an `AtomicBoolean`
  parameter. That was the PR description's first design, not the merged code.)*

## Prioritized next improvements

| # | Improvement | Entries affected | Why |
|---|---|---|---|
| 1 | **Detect modifier and visibility changes** as their own kind (F1). | 2+ | The only entry with near-zero coverage, and visibility is API. |
| 2 | **Custom-named Gradle build files** (F4, was N7). | 3 repositories | Now the top architecture gap, on the entries most likely to be cross-module. |
| 3 | **Framework classification hygiene:** whole-token annotation matching, and no or grouped framework cards for test code (F3). | 1 heavily (218 cards) | Cheap, and removes the largest noise source in the expanded corpus. |
| 4 | **Compare field initializers** (F2). | 1+ | Constant tables and registries are common feature switches. |
| 5 | **Unique module names** (F5): fall back to the relative path when names collide. | 1 | Correctness of module-level views. |
| 6 | **Bound the Canvas's first view** (N6, from the third run). | 5 | Still open. The expansion adds netty (+9 idle) and guava (+3). |
| 7 | **Loop-restructuring awareness** (F6) and **anonymous-class members** (F7). | 2 | Say when a behavioral change keeps the same operations, and show cached state added in anonymous classes. |

Outside the deterministic engine: the closed-unmerged entries show that the questions
reviewers reject PRs over (leaks, performance, design trade-offs) need reasoning about
consequences. An AI layer that is *constrained to Athena's evidence*, with each claim
anchored to a Change, is the natural next experiment. It should be scored separately, as
`PROTOCOL.md` requires.

## Reproducing

```bash
evaluation/tools/run-corpus.sh          # all 26 entries, ~4 minutes at c6a1721
evaluation/tools/coverage.py evaluation/snapshots/$(git rev-parse --short HEAD)/*
```

## Limitations

- **Single agent evaluator**, reading-effort proxies, and AI features excluded, as in every
  report. The diff-side scores for the new entries were set in the same pass as the Athena
  side, so the two are less independent than in the original baseline.
- **Rejection reasons come from the public review threads**, summarized. Where a thread was
  long (commons-io-866, mockito-3843), only its concluding arguments were used.
- **gson-3054's close was technically accidental** (a deleted fork). It was kept because the
  review had already asked for the simpler design that was merged as gson-3086, and the pair
  is the clearest test of the closed-unmerged question.
