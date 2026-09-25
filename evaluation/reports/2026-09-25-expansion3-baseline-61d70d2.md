# Athena PR evaluation: baseline for the third corpus expansion (21 new entries)

| | |
|---|---|
| Date | 2026-09-25 |
| Athena version | `61d70d2` (main after #351/#352, plus this batch's corpus rows; no engine change since `6bd7b5b`) |
| Scope | The 21 entries added in #355: 17 merged and 4 `closed-unmerged` PRs from 9 repositories new to the corpus (kafka, camel, jetty, quarkus, nacos, openrewrite, jenkins, testcontainers-java, assertj) |
| Snapshots | `evaluation/snapshots/61d70d2/`, all 66 entries |
| Protocol | `evaluation/PROTOCOL.md`, unchanged |
| Evaluator | Claude (agent) |

> **Why this report matters.** Every entry of the first two expansions has driven a fix, so
> neither measures generalization any more. These 21 were chosen and snapshotted **before**
> any fix motivated by them. They are the only clean out-of-sample check, and they should
> stay clean until this report's findings become tickets.

## Executive summary

All 66 entries ran `READY`. Two of the new ones needed a retry. GitHub reset one connection
(kafka-23339), and another fetch stalled silently for 12 minutes until it was killed
(nacos-15628). Both succeeded on retry at the same commits. The stall exposed a robustness gap
(K7). The 21 new entries took 4–60 s each.

| Measure | Third expansion, merged (17) | Third expansion, closed (4) | *Second expansion, merged (15), at its baseline* |
|---|---|---|---|
| Athena / diff score | **120 / 136 (88 %)** | **25 / 32 (78 %)** | *88 / 117 (75 %)* |
| PRs where Athena ≥ the diff | **8 of 17** (+2 within 1) | 1 of 4 | *6 of 15* |
| Median production-Java coverage | **1.00** | 1.00 | *1.00* |
| Worst production-Java coverage | **0.97** (camel-26806) | 1.00 | *0.18* |

**The headline: Athena generalizes better than at the last out-of-sample check, but not on
small bug fixes.**
- **The gain:** it goes from 75 % of the diff's score to 88 %.
- **Where it comes from:** mostly the cross-module, dependency-framework, architectural, large and mixed PRs, where Athena beat the diff on all five. Earlier fixes carry over to unseen repositories:
  - folding reads spread-out work as one operation: `Add getDefaultDriverClassName in 6 classes` (nacos-15856), `Add onAcceptFailed in 3 classes` (jetty-15668), `Add getNodeSource in 4 classes` (camel-26805);
  - #336's rename detection: `Rename GradleWrapper#migrateToDownloadsUrl -> toDownloadsHost` (openrewrite-8915);
  - #340's dependency changes: `Remove dependency org.mockito:mockito-inline` (nacos-15628);
  - #339/#351's return summaries: `return password -> password.toCharArray()` (quarkus-56904).
- **Small bug fixes still trail badly.** jenkins, kafka and nacos score **13 / 29 (45 %)**, the same pattern as every earlier batch. On a two-line fix the diff already explains itself, and Athena's summary of the changed line is the weakest part of its view (K2, K3, K5).

Part of the higher ratio is the batch's mix: it has more of the categories where Athena is
strong. Categories aren't balanced between batches, so the 88 % isn't directly comparable to
the 75 %. The small-bug-fix figure is.

**Six new findings (K1–K6) and one robustness gap (K7):**

1. **K1: Kafka's modules collapse into one territory.** Kafka declares its projects as `project(':clients') { … }` blocks in a single root `build.gradle`, without per-module build files. All four kafka entries show one `kafka` territory, so the Canvas can't tell group-coordinator from streams.
2. **K2: A changed argument or condition inside a statement is "other statements changed".** kafka-23538's whole fix swaps the two operands of a ternary passed to `validateMember(…)`: `isTransactional ? "offset-commit" : "txn-offset-commit"` becomes the reverse. Body summaries cover calls, throws, returns and statement order, but not arguments.
3. **K3: "; same calls" softens an added guard.**
   - nacos-15873's fix is `if (client instanceof ConnectionBasedClient) continue;`. It reads `branch added; same calls`, and jetty-15668's `ClientConnector#connectFailed` reads the same way.
   - The suffix (#319) was meant for restructured control flow, like an unrolled loop. On a new early-exit guard it understates a behavior change, the same kind of problem as H1.
4. **K4: A changed superclass isn't reported at all.** camel-26819 changes `SimpleRegistry extends LinkedHashMap` to `extends ConcurrentHashMap`, which is its central thread-safety change. There's no transformation kind for a supertype change, so the view shows only `SimpleRegistry#bind: other statements changed`.
5. **K5: Calls are named without their receiver.** kafka-23570's one-line root-cause fix reads `Modify body of StreamTask#close: +clear`. It is correctly the first focus area, but it doesn't say *what* is cleared (`consumedOffsets`), which is the whole point.
6. **K6: A test-only refactoring is one card per test class.**
   - assertj-4338 changes 30 test files the same way and yields 25 Explorer cards, one `Test changes in X` per class, where folding across classes would read as one operation.
   - Its top focus area is an internal-sounding `Rename BaseAssertionsTest#<instance-init> -> setUpStackTraceFiltering`.
7. **K7: A stalled `git fetch` hangs the analysis indefinitely.**
   - `GitRevisionCheckout` runs `git fetch --depth 1` without a timeout. One silently dead connection hung this run for 12 minutes, and in the product it would hang a user's analysis the same way.
   - A connection reset fails the entry outright, with no retry.

**Closed-unmerged: the rejection reason is visible in 2 of 4, hinted in 1, invisible in 1.**
This is the best result of any batch so far:
- **Visible, nacos-15628:** titled "add logging", yet Athena's short list shows `Remove ProtoMessageUtil#convertToReadRequest/WriteRequest`, `return statements 3 → 2` and `Remove dependency org.mockito:mockito-inline`. Those are exactly the two breaking changes the reviewer objected to, in contradiction with the title.
- **Visible, jetty-15616:** `Add field ServerFCGIConnection#lock`, `onCompleted: +lock`, and `Extract onFillableLocked`/`parseAndFillLocked`. The coarse lock the reviewer rejected is the most visible thing on the page, and side by side with jetty-15648's deferred dispatch the difference in approach is clear.
- **Hinted, kafka-23339:** `maybeFallBackToZeroCommittedOffset`, `committedOffsetMissingSinceMs` and `taskTimeoutMs` read as retry machinery. Next to kafka-23570's one-line `close: +clear`, "treating the symptom" is suggested but not stated.
- **Invisible, camel-26819:** the reviewer found a remaining reader-side race and a symptom-masking null guard. The superclass change is missing (K4), and nothing in the view points at the reader path.

## Metrics

| Entry | Category | Files (prod Java) | Changes (test) | Cards | Prod coverage | Represented | Territories (+idle) | Time |
|---|---|---|---|---|---|---|---|---|
| jenkins-27381 | small-bug-fix | 2 (1) | 6 (5) | 2 | 1.00 | 2/2 | 1 (+3) | 9 s |
| kafka-23538 | small-bug-fix | 2 (1) | 2 (1) | 2 | 1.00 | 2/2 | 1 (+1) (K1) | 17 s |
| nacos-15873 | small-bug-fix | 2 (1) | 3 (2) | 3 | 1.00 | 2/2 | 1 (+3) | 6 s |
| testcontainers-11970 | simple-feature | 3 (1) | 10 (7) | 6 | 1.00 | 2/3 | 1 | 4 s |
| camel-26831 | simple-feature | 2 (1) | 3 (1) | 5 | 1.00 | 2/2 | 1 (+3) | 50 s |
| assertj-4338 | refactoring | 30 (0) | 46 (38) | **25** (K6) | n/a | 27/30 | 2 | 6 s |
| nacos-15854 | refactoring | 5 (1) | 25 (21) | 9 | 1.00 | 3/5 | 2 (+10) | 13 s |
| quarkus-56904 | api-change | 6 (2) | 18 (10) | 10 | 1.00 | 3/6 | 2 (+5) | 34 s |
| camel-26805 | cross-module | 19 (10) | 29 (13) | 15 | 1.00 | 13/19 | 6 (+35) | 49 s |
| nacos-15856 | dependency-framework | 19 (8) | 66 (52) | 15 | 1.00 | 15/19 | 6 (+4) | 13 s |
| jetty-15668 | architectural | 8 (7) | 49 (20) | 27 | 1.00 | 8/8 | 4 (+11) | 14 s |
| camel-26806 | large | 63 (35) | 179 (73) | 113 | 0.97¹ | 45/63 | 4 (+34) | 60 s |
| openrewrite-8915 | mixed-noisy | 11 (2) | 47 (40) | 13 | 1.00 | 6/11 | 2 (+9) | 17 s |
| jetty-15648 | complex-behavioral | 2 (2) | 5 (0) | 9 | 1.00 | 2/2 | 1 (+6) | 14 s |
| camel-26818 | complex-behavioral | 2 (1) | 16 (12) | 8 | 1.00 | 2/2 | 2 (+34) | 48 s |
| kafka-23570 | complex-behavioral | 2 (1) | 43 (42) | 2 | 1.00 | 2/2 | 1 (+1) (K1) | 25 s |
| kafka-23542 | complex-behavioral | 3 (1) | 25 (21) | 8 | 1.00 | 3/3 | 1 (+1) (K1) | 31 s |
| kafka-23339 | closed-unmerged | 2 (1) | 15 (5) | 12 | 1.00 | 2/2 | 1 (+1) (K1) | 16 s² |
| nacos-15628 | closed-unmerged | 4 (1) | 16 (10) | 8 | 1.00 | 4/4 | 3 (+36) | 12 s² |
| jetty-15616 | closed-unmerged | 2 (1) | 9 (3) | 10 | 1.00 | 2/2 | 1 (+6) | 14 s |
| camel-26819 | closed-unmerged | 4 (2) | 7 (4) | 6 | 1.00 | 4/4 | 3 (+36) | 49 s |

¹ `FileConstants.java` in camel-26806 is unrepresented.
² On the retry; the first attempts failed on the network (K7).

The unrepresented files are otherwise documentation (`.adoc`, `.md`), generated catalog JSON,
`.properties`, `gradle-wrapper.properties`, service-loader files and one `module-info.java`
with no semantic change. They are known, unsupported types.

**The existing 45 entries:** no regressions. Five differ from `feaac14`, and only in wording
expected from #351/#352: junit5-6057, guava-8647, mockito-3843 (`return value changed ×2`),
error-prone-6105 and vertx-6367.

## Scores (What + Why + Impact, 0–12 each side)

| Entry | Diff | Athena | vs. diff | Notes |
|---|---|---|---|---|
| jenkins-27381 | 10 | 5 | worse | `QueryParameterMap#<init>: +isEmpty` and the test name `parameterWithoutValue` locate it; the guard itself isn't described. |
| kafka-23538 | 10 | 3 | worse | "Other statements changed" for a swapped ternary, the entire fix (K2). |
| nacos-15873 | 9 | 5 | worse | The right method and a telling test name (`…IncludeNonConnectionBasedClient`), but the guard reads `; same calls` (K3). |
| testcontainers-11970 | 10 | 9 | within 1 | `withCommandOptions`, the new field, `loop added` in `getCommandLine`, and `configureAppliesCommandOptionsEvenIfWithCommandWasUsed`: the feature and its reason. |
| camel-26831 | 10 | 8 | worse | `branch added` in `prepareExchange`, `Add firstPlaceholder`, and the unresolved-placeholder test: the check is legible, the error message isn't. |
| assertj-4338 | 6 | 5 | worse | The removed `MutatesGlobalConfiguration` is visible, but 25 per-class cards bury the one pattern (K6). |
| nacos-15854 | 8 | 8 | equal | Removed `convertToReadRequest/WriteRequest`, `parse: branch added`, and tests named `…PreservesCause`/`…InvalidLogRollsBack…` tell the story. |
| quarkus-56904 | 9 | 9 | equal | `+@Deprecated` on the `String` methods, the new overloads, and `return password -> password.toCharArray()` delegation: the API migration in one view. The `char[]` parameter type itself isn't named. |
| camel-26805 | 7 | **9** | **better** | `Add getNodeSource in 4 classes` plus the console/JMX signature changes across 6 modules, out of 19 files with generated JSON. |
| nacos-15856 | 7 | **10** | **better** | `Add getDefaultDriverClassName in 6 classes`, `DEFAULT_DRIVER_CLASS_NAME in 4 classes` and `resolveDefaultDriverClassName`: the SPI extension across every dialect plugin at a glance. |
| jetty-15668 | 6 | **8** | **better** | `Add onAcceptFailed in 3 classes` and `Add close in 3 classes` name the new failure path and the `Closeable` tasks, out of 8 dense selector files. |
| camel-26806 | 4 | **5** | **better** | 113 cards is heavy, but the control-flow changes per expression builder map the ~45 fixes better than 2.3k diff lines. No per-bug *why*. |
| openrewrite-8915 | 6 | **7** | **better** | `Rename migrateToDownloadsUrl -> toDownloadsHost`, `Add withHostOf` and the removed URL constant, with 40 fixture changes folded. The wrapper properties are unread. |
| jetty-15648 | 8 | 7 | within 1 | `onHeaders` signature change, `Remove execute`, `Add field onRequest`, and `onFillable: branch added` together read as "dispatch moved out of the callback". |
| camel-26818 | 9 | 7 | worse | `Extract aggregateCompleted`, `Add doDoneNoMorePairs` and `run: +doDoneNoMorePairs, -doDone` locate the completion fix; the null-part trigger is only in the test name. |
| kafka-23570 | 9 | 7 | worse | `StreamTask#close: +clear` is correctly the first focus area, but it doesn't say what is cleared (K5). The integration test name carries the why. |
| kafka-23542 | 8 | 8 | equal | `Add throwIfMemberIdHasDifferentInstanceId`, two `branch added`, and a family of `…CannotRejoinWith…` tests: the new rejection rule is legible. |
| **Merged total** | **136** | **120** | | |
| kafka-23339 | 8 | 6 | worse | Reason *hinted*: retry and fallback machinery, next to kafka-23570's one-line fix. |
| nacos-15628 | 8 | 8 | equal | Reason *visible*: removals and a dropped test dependency under an "add logging" title. |
| jetty-15616 | 8 | 7 | worse | Reason *visible*: the lock field, `+lock`, and the `*Locked` extractions. |
| camel-26819 | 8 | 4 | worse | Reason *invisible*: the superclass swap is missing (K4); the reader-side race isn't suggested. |
| **Closed total** | **32** | **25** | | |

## Prioritized next improvements

| # | Improvement | Entries affected | Why |
|---|---|---|---|
| 1 | **Don't soften added guards with "; same calls"** (K3). | 2 | Correctness: the suffix understates a behavior change, the H1 pattern again. Cheap. |
| 2 | **Report supertype changes** (K4): `extends`/`implements` added, removed or changed. | 1 (+ likely common) | A class's contract and behavior can change entirely through its supertype, and today that's invisible. |
| 3 | **Fetch timeout and a retry** (K7). | 2 in this run | Robustness in the product itself, not just the corpus: a dead connection must fail fast, not hang an analysis. |
| 4 | **Name a changed argument or condition, and the receiver of a call** (K2, K5). | 2 | These two are where small bug fixes, Athena's weakest category, are lost. |
| 5 | **Modules declared in a root `build.gradle`** (K1). | 4 (kafka) | Restores the Canvas for projects built like kafka's. |
| 6 | **Fold identical test changes across test classes** (K6). | 1 | Wide test-only refactorings read as one operation. |

Unchanged from earlier reports: the *why* of small fixes and most rejection reasons need
consequence-level reasoning (an evidence-grounded AI layer, scored separately).

## Reproducing

```bash
evaluation/tools/run-corpus.sh          # all 66 entries; the new ones take 4–60 s each at 61d70d2
evaluation/tools/run-corpus.sh kafka-23339 nacos-15628   # re-run entries that failed on the network
evaluation/tools/coverage.py evaluation/snapshots/$(git rev-parse --short HEAD)/*
```

## Limitations

- **Single agent evaluator.** The same agent selected the PRs, set both sides' scores and implemented the fixes whose effects are measured here. This is the batch where that matters most, because it's the out-of-sample check. Blind or independent scoring of at least a sample would make the 88 % more trustworthy.
- **Batch mix.** Categories aren't balanced between batches, so compare ratios per category (small bug fixes: 45 %), not overall.
- **Output, not UI.** Scores judge the JSON behind the Change Map, Explorer, Canvas and focus areas. They don't judge how it renders, or what a reviewer actually notices.
- **Rejection reasons are summaries** of each PR's public review thread.
- **Keep this batch clean.** Once K1–K7 become tickets with these entries as definitions of done, the batch stops being out-of-sample, like the two before it.
