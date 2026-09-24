# Athena PR evaluation: baseline for the second corpus expansion (19 new entries)

| | |
|---|---|
| Date | 2026-09-24 |
| Athena version | `b6f6f49` (main; same engine as `9d1b462`, only docs changed in between) |
| Scope | The 19 entries added in #332: 15 merged and 4 `closed-unmerged` PRs from 9 repositories new to the corpus (commons-collections, dubbo, error-prone, spring-security, maven, vert.x, lucene, dropwizard, micronaut-core) |
| Snapshots | `evaluation/snapshots/b6f6f49/`, all 45 entries |
| Protocol | `evaluation/PROTOCOL.md`, unchanged |
| Evaluator | Claude (agent) |

> **Why this report matters.** The 16 entries from the first expansion became this batch's
> definitions of done (#313–#320), so they no longer measure generalization. These 19 were
> chosen and snapshotted **before** any fix motivated by them. They are the clean
> out-of-sample check.

## Executive summary

All 45 entries ran `READY`; the 19 new ones took 4–20 s each. Every closed PR's head commit
was still fetchable.

| Measure | Original 10 | First expansion (16) | **Second expansion, merged (15)** | **Second expansion, closed (4)** |
|---|---|---|---|---|
| Athena / diff score | 86 / 85 | 109 / 125 | **88 / 117 (75 %)** | **23 / 34 (68 %)** |
| PRs where Athena ≥ the diff | 5 of 10 | 5 of 16 | **6 of 15** (+1 within 1) | 0 of 4 |
| Median production-Java coverage | 1.00 | 0.98 / 1.00 | **1.00** | 1.00 |
| Worst production-Java coverage | 0.80 | 0.67 | **0.18** (vert.x-6339) | 1.00 |

**The headline: the out-of-sample picture matches the first expansion's before its fixes.**
Athena reaches about 75 % of the diff's score on unseen PRs, the same as the first expansion's
80 % before #313–#320. The earlier fixes do generalize where they apply:
- Folding reads repeated work as one operation: `Change Control Flow calculateDegreeOfConcurrency in 2 classes` (maven) and `Add IGNORED_BACKGROUND_REQUEST_PATTERNS in 3 classes` (spring-security).
- Annotation elements are detected: `Add annotation element RestrictedApi#allowedPaths` (error-prone-6125).
- Modifier changes are detected: `+static` on vert.x's QUIC dispatchers.
- Gradle Kotlin-DSL modules are found: error-prone's `check_api`/`core`/`annotations`.

As before, Athena beats the diff on the large and architectural PRs: vert.x-6367, lucene-16696, error-prone-6125. It trails on small bug fixes, where the diff already explains itself.

**Six new findings.** One of them is a regression from the previous batch (H1):

1. **H1: "same calls" hides an added throw.** dubbo-16299 adds `throw new RemotingException(...)` to
   `getInvoker`, which already throws that type four times elsewhere. #319 compares only the
   *set* of thrown types, so the fix reads `branch added; same calls`. That's wrong, and it
   lands on exactly the kind of change the suffix must never soften.
2. **H2: Build files named after the project, not the directory.** spring-security's
   `web/spring-security-web.gradle` (its `settings.gradle` maps each `*.gradle` file to a
   project) isn't recognized by #314's `<directory>.gradle` rule. So `web` and `config` collapse
   into one `spring-security` territory.
3. **H3: Package moves read as nonsense, and their follow-on edits as nothing.**
   - vert.x-6339 moves `ServiceResource` from `impl` to `internal`, which reads `Move class ServiceResource -> ServiceResource`, with no packages shown.
   - Its rename `CleanableObject` → `CleanableResource`, combined with the move, reads as a remove plus an add.
   - The nine files whose only edit is the updated import are "no semantic change detected". Production coverage is **0.18**.
4. **H4: Statement order and returned values aren't summarized.** commons-collections-716's
   entire fix moves `modCount++` below the bounds check, and dubbo-16350's returns `""` instead
   of the input. Both read "other statements changed".
5. **H5: A build-only PR is almost empty.** dropwizard-11325 changes 29 `pom.xml` files and no
   Java. Athena lists them as unsupported files, and that's all (a known gap: build files
   aren't interpreted).
6. **H6: Groovy/Spock tests aren't read.** micronaut-13421's regression test is a Spock spec,
   so it's "unsupported file type".

**Closed-unmerged.** The rejection reason is visible in 1 of 4, hinted in 2, invisible in 1:
- **Visible, vert.x-6303:** the reviewer rejected it as "a speculative fix" without a test, and Athena's view shows exactly that: one body edit (`+succeededFuture`) and no test Change at all.
- **Hinted, vert.x-6308:** `DnsClientImpl#<init>: +getAddress, +getHostAddress, -getHostString` points at address resolution, but not at *blocking* resolution.
- **Hinted, dubbo-16298:** it changes both codec encode paths, against the merged dubbo-16299's single guard in `getInvoker`. The broader blast radius is visible side by side; the "swallowing other exceptions" risk isn't.
- **Invisible, commons-collections-674:** it was rejected because a better fix already existed elsewhere.

## Metrics

| Entry | Category | Files (prod Java) | Changes (test) | Cards | Prod coverage | Represented | Territories (+idle) | Time |
|---|---|---|---|---|---|---|---|---|
| commons-collections-716 | small-bug-fix | 2 (1) | 3 (1) | 3 | 1.00 | 2/2 | 1 | 4 s |
| dubbo-16350 | small-bug-fix | 2 (1) | 2 (1) | 2 | 1.00 | 2/2 | 1 | 8 s |
| dubbo-16299 | small-bug-fix | 2 (1) | 2 (1) | 3 | 1.00 | 2/2 | 1 (+6) | 10 s |
| error-prone-6093 | simple-feature | 2 (1) | 7 (2) | 8 | 1.00 | 2/2 | 1 (+5) | 7 s |
| spring-security-19696 | simple-feature | 6 (3) | 29 (21) | 10 | 1.00 | 6/6 | **1** (H2) | 10 s |
| error-prone-6105 | refactoring | 11 (9) | 71 (4) | 73 | 1.00 | 11/11 | 2 (+4) | 6 s |
| error-prone-6125 | api-change | 6 (4) | 32 (16) | 21 | 1.00 | 6/6 | 3 (+3) | 6 s |
| maven-13229 | cross-module | 7 (5) | 8 (4) | 6 | 0.80¹ | 6/7 | 2 (+25) | 17 s |
| dubbo-16313 | dependency-framework | 2 (1) | 3 (2) | 3 | 1.00 | 2/2 | 1 (+6) | 8 s |
| dropwizard-11325 | dependency-framework | 29 (0) | **0** | 0 | n/a | 0/29 | 0 | 4 s |
| vertx-6367 | architectural | 10 (7) | 93 (5) | 108 | 0.86 | 9/10 | 1 (+1) | 5 s |
| lucene-16696 | large | 9 (5) | 137 (94) | 46 | 1.00 | 8/9 | 1 (+4) | 20 s |
| vertx-6339 | mixed-noisy | 13 (11) | 31 (6) | 40 | **0.18** | 6/15 | 1 (+1) | 5 s |
| vertx-6352 | complex-behavioral | 4 (2) | 8 (2) | 10 | 1.00 | 4/4 | 2 (+1) | 4 s |
| micronaut-13421 | complex-behavioral | 2 (1) | 4 (0) | 6 | 1.00 | 1/2 (Groovy spec) | 1 | 11 s |
| vertx-6308 | closed-unmerged | 2 (1) | 2 (1) | 2 | 1.00 | 2/2 | 1 (+1) | 4 s |
| vertx-6303 | closed-unmerged | 1 (1) | 1 (0) | 1 | 1.00 | 1/1 | 1 (+1) | 4 s |
| dubbo-16298 | closed-unmerged | 2 (1) | 4 (2) | 4 | 1.00 | 2/2 | 1 (+6) | 10 s |
| commons-collections-674 | closed-unmerged | 2 (1) | 5 (3) | 3 | 1.00 | 2/2 | 1 | 4 s |

¹ `MavenOptions.java` only had Javadoc edits, which is correct.

## Scores (What + Why + Impact, 0–12 each side)

| Entry | Diff | Athena | vs. diff | Notes |
|---|---|---|---|---|
| commons-collections-716 | 10 | 5 | worse | Two "other statements changed" on `TreeList#add/remove`. The test name (`testFailedIndexedChangeKeepsIteratorValid`) carries the *why*; the reorder itself is invisible (H4). |
| dubbo-16350 | 11 | 4 | worse | "Other statements changed" for a changed return value (H4). |
| dubbo-16299 | 10 | 5 | worse | The right method and a telling test name, but mislabelled `; same calls` (H1). |
| error-prone-6093 | 9 | 7 | worse | The enum constant, flag and matcher that make up the new check are all named. |
| spring-security-19696 | 9 | 8 | within 1 | `getFaviconRequestMatcher` → `getIgnoredBackgroundRequestMatcher` plus `IGNORED_BACKGROUND_REQUEST_PATTERNS in 3 classes` reads as the feature; 21 test changes folded. One territory (H2). |
| error-prone-6105 | 6 | 6 | equal | Signature changes across `MoreAnnotations` are clear; 73 cards is heavy for a refactoring. |
| error-prone-6125 | 7 | **9** | **better** | `Add annotation element RestrictedApi#allowedPaths` and the `Restriction` class across 3 modules: the API and its enforcement in one view. |
| maven-13229 | 7 | 7 | equal | The template-only description makes the diff hard; Athena's folded `calculateDegreeOfConcurrency` in 2 classes names the actual change. |
| dubbo-16313 | 10 | 5 | worse | "Condition changed" on `addOrReplace` names the spot, not the immutable-map fix. |
| dropwizard-11325 | 6 | 1 | worse | Nothing but 29 unsupported files (H5). |
| vertx-6367 | 5 | **7** | **better** | New `TcpServer`/`ChannelHandler` classes, `Remove sharedTcpServers in 3 classes`, and the event-loop-group changes: the redesign's outline, out of 813 lines. |
| lucene-16696 | 4 | **7** | **better** | `MergeScorerData`, `PreparedQueryData`, the extracted `writeCorrections`, and branch removals in the writer, out of 1.8k lines, with 94 test changes folded. |
| vertx-6339 | 7 | 4 | worse | Package-less "Move class X -> X", a rename read as remove plus add, and 9 import-only files "no semantic change" (H3). |
| vertx-6352 | 7 | 7 | equal | `ST_RESUMED -> ST_DONE`, new `await`/`interrupted`, and `resume` signature changes: the state-machine fix is legible. |
| micronaut-13421 | 9 | 6 | worse | The right methods, including the new `shouldBufferErrorBody`; "release the connection" isn't stated. Spock test unread (H6). |
| **Merged total** | **117** | **88** | | |
| vertx-6308 | 8 | 6 | worse | Reason *hinted* (resolution APIs visible, blocking not). |
| vertx-6303 | 9 | 5 | worse | Reason *visible*: one body edit, no test. |
| dubbo-16298 | 8 | 6 | worse | Reason *hinted* (blast radius visible next to dubbo-16299). |
| commons-collections-674 | 9 | 6 | worse | Reason *invisible* (a better fix existed elsewhere). |
| **Closed total** | **34** | **23** | | |

## Prioritized next improvements

| # | Improvement | Entries affected | Why |
|---|---|---|---|
| 1 | **Fix "same calls" to compare thrown-type counts** (H1). | 1+ | A regression from #319 that softens a real fix. It should be fixed before anything else. |
| 2 | **Package-qualified moves, move-plus-rename, and import-only follow-ons** (H3). | 1 heavily (coverage 0.18) | Package reorganizations are common and currently read as nonsense. |
| 3 | **Build files mapped by `settings.gradle`** (H2). | 1+ | Generalizes #314 to projects whose build file is named after the project, not the directory. |
| 4 | **Statement-order and returned-value summaries** (H4). | 2 | Small bug fixes are exactly where Athena trails the diff most. |
| 5 | **Build-file interpretation** (H5, long-standing) and **Groovy/Spock test support** (H6). | 2+ | Language and format coverage. |

Unchanged from the earlier reports: rejection reasons and *why* on small fixes need
consequence-level reasoning (an evidence-grounded AI layer, scored separately).

## Reproducing

```bash
evaluation/tools/run-corpus.sh          # all 45 entries; each takes 3–21 s at b6f6f49
evaluation/tools/coverage.py evaluation/snapshots/$(git rev-parse --short HEAD)/*
```

## Limitations

- **Single agent evaluator.** The diff-side and Athena-side scores were set in the same pass,
  so they're less independent than the original baseline's.
- **Summarized rejection reasons.** The closed-unmerged reasons are summaries of the public
  review threads' concluding arguments.
- **Keep this batch clean.** Once these 19 become definitions of done for fixes, they stop
  being out-of-sample. The next measurement of generalization will need another fresh set.
