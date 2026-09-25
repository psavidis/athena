# Athena PR evaluation: baseline for the fourth corpus expansion (21 new entries)

| | |
|---|---|
| Date | 2026-09-25 |
| Athena version | `8a15ab4` (main at `a985f71`, after #374–#377, plus this batch's corpus rows; no engine change) |
| Scope | The 21 entries added in #382: 17 merged and 4 `closed-unmerged` PRs from 8 repositories new to the corpus (pulsar, iceberg, rocketmq, redisson, trino, zookeeper, resilience4j, elasticsearch) |
| Snapshots | `evaluation/snapshots/8a15ab4/`, all 87 entries |
| Protocol | `evaluation/PROTOCOL.md`, unchanged |
| Evaluator | Claude (agent) |

> **Why this report matters.** All three earlier expansions have driven fixes, so none of them
> measures generalization any more. These 21 were chosen, snapshotted and read diff-first
> **before** any fix motivated by them. They are the only clean out-of-sample check, and they
> should stay clean until this report's findings become tickets.

## Executive summary

All 87 entries ran `READY` on the first attempt. The 21 new ones took 3–67 s each; elasticsearch
was the slowest at 67 s. No fetch stalled or failed, so #359's retry wasn't needed.

| Measure | Fourth expansion, merged (17) | Fourth expansion, closed (4) | *Third expansion, merged (17), at its baseline* |
|---|---|---|---|
| Athena / diff score | **128 / 156 (82 %)** | **24 / 33 (73 %)** | *120 / 136 (88 %)* |
| PRs where Athena ≥ the diff | **7 of 17** (+3 within 1) | 1 of 4 | *8 of 17* |
| Small bug fixes (Athena / diff) | **16 / 30 (53 %)** | | *13 / 29 (45 %)* |
| Median production-Java coverage | **1.00** | 1.00 | *1.00* |
| Worst production-Java coverage | **0.46** (zookeeper-2435) | 1.00 | *0.97* |

**The headline: Athena generalizes roughly as it did at the last out-of-sample check. It is a
little weaker overall and a little better on small bug fixes, and there are new blind spots.**
- **The ratio:** 82 % of the diff's score, against 88 % at the third baseline.
- **Where the tuned fixes carry over:**
  - folding and pull-up: `Pull up … into AbstractConsumeMessageService (from 4 classes)` and `Change Supertype +AbstractConsumeMessageService in 4 classes` (rocketmq-11090);
  - #340/#358: `Remove Dependency org.eclipse.jetty:jetty-servlet in 4 modules` and `Add Dependency …jetty-ee10-servlet in 4 modules` (zookeeper-2435);
  - #360's receivers: `+sourceData.getRowKind` (iceberg-18196).
- **Where it slips:** mostly on constructs the earlier corpora never had:
  - field assignments (N1);
  - Lua scripts in Java strings (N2);
  - class-to-record conversions (N3);
  - a type-level annotation (N4);
  - `javax` → `jakarta` package moves (N5);
  - a fix repeated across parallel modules (N6);
  - Gradle dependency files (N7).
- **Small bug fixes still trail:** 53 %. Two of the three are assignment fixes that read as "other statements changed" (N1).

Categories aren't balanced between batches, so compare per category, not overall.

**Seven new findings (N1–N7), and two smaller observations (N8, N9):**

1. **N1: An added or changed field assignment reads as "other statements changed".**
   - pulsar-26707's whole fix is `entry.position = null;` in three `create` methods, and it reads `Modify body of EntryImpl#create: other statements changed ×3`.
   - resilience4j-2507's constructor now copies seven fields instead of keeping a reference, and reads the same way.
   - Body summaries (#288, #339, #360, #361) cover calls, throws, returns, order and arguments, but not assignments.
2. **N2: A Lua script inside a Java string reads as "return value changed".** redisson-7343 and redisson-7334 change the rate limiter's Lua script. Both read `return value changed`, which is literally true (the method returns the script call) but says nothing. The two competing fixes can't be told apart.
3. **N3: A class-to-record conversion reads as a mass removal.**
   - trino-31311 turns nine value classes into records. It shows `Remove equals in 9 classes`, `Remove hashCode in 9 classes` and 22 `Remove` cards.
   - Worse, **45 inferred `Remove Capability`** entries claim capabilities were removed. That's misleading: the refactoring removes nothing.
4. **N4: A type-level annotation change isn't reported.** pulsar-26646's point is removing `@InterfaceAudience.Private` from `TransactionCoordinatorClient`, which makes it public API. That file is unrepresented, so the view shows only the new getter.
5. **N5: Moving a type to another package with the same simple name is invisible.** zookeeper-2435's `javax.servlet` → `jakarta.servlet` migration changes method signatures in seven production files (`Commands`, `AuthenticationProvider`, …). None of them is represented: **production coverage 0.46**, the worst in any baseline since `vertx-6339`.
6. **N6: The same fix in parallel modules collapses into one module.** iceberg-18196 makes an identical one-line change in `flink/v1.20`, `v2.1` and `v2.2`. It folds into one `×3` Change, and the Canvas shows only `v1.20`, so the backport across three versions isn't visible as such.
7. **N7: Gradle dependency changes aren't read.** iceberg-18195's `apache-client` → `apache5-client` swap in `aws-bundle/build.gradle` and the version catalog is unrepresented. #340 reads Maven only. Athena shows the type swap in two classes, but not the dependency change that causes it.

Smaller:

8. **N8: A try/finally-style restructuring reads as "condition changed".** trino-31334 wraps the release in a `Closer` so it runs even if the flush throws. The view says only `finish: condition changed`, and the "released on failure" point lives in the test name.
9. **N9: Gradle projects remapped with `projectDir` get no rails and an Unknown tech stack.**
   - iceberg's `settings.gradle` maps `:iceberg-core` to `core/`. The #376 root blocks name the project, not the directory, so no iceberg module gets a rail and all read `Unknown`.
   - Separately, large Maven reactors now draw dense Canvases: 13 idle neighbours for trino-31334's two-file fix, and 23 for elasticsearch-160228.

**Closed-unmerged: the rejection reason is visible in 0 of 4, hinted in 1, invisible in 3.** This
is the weakest result of any batch. The third expansion scored 2 / 1 / 1.
- **Hinted, pulsar-26459:** `Encoder#write: +compositeBuffer, +frame.addComponent, +first.retain…` shows a composite buffer being introduced. A reader who knows `ByteBufPair` exists to avoid composites would pause. The actual objection, Netty ownership on the release side, needs the companion `pulsar-26456`.
- **Invisible, redisson-7334:** `availablePermitsAsync: return value changed` (N2). The clamp-versus-root-cause question can't be seen, and next to `redisson-7343`'s `releaseAsync: return value changed` the two look alike.
- **Invisible, zookeeper-2401:** `return Arrays.equals -> MessageDigest.isEqual` states the change exactly. The reason it's pointless, a password derivable from the session id, lives in `generatePasswd`, outside the PR.
- **Invisible, iceberg-18169:** `toString: branch added`, `SchemaParser#toJson: branch added`, and two `writeReplace` additions. The spec question the maintainers settled on the mailing list has no trace in the code.

## Metrics

| Entry | Category | Files (prod Java) | Changes (test) | Cards | Prod coverage | Represented | Territories (+idle), rails | Time |
|---|---|---|---|---|---|---|---|---|
| pulsar-26707 | small-bug-fix | 2 (1) | 2 (1) | 2 | 1.00 | 2/2 | 1 (+4), 4 | 12 s |
| resilience4j-2507 | small-bug-fix | 2 (1) | 3 (2) | 2 | 1.00 | 2/2 | 1 (+2), 2 | 3 s |
| redisson-7343 | small-bug-fix | 2 (1) | 2 (1) | 2 | 1.00 | 2/2 | 1, 0 | 5 s |
| redisson-7358 | simple-feature | 9 (5) | 11 (5) | 5 | 1.00 | 9/9 | 1, 0 | 6 s |
| rocketmq-11079 | simple-feature | 3 (2) | 9 (4) | 8 | 1.00 | 3/3 | 2 (+7), 7 | 6 s |
| trino-31311 | refactoring | 35 (33) | 77 (2) | 56 (N3) | 1.00 | 35/35 | 4 (+16), 32 | 22 s |
| pulsar-26646 | api-change | 10 (4) | 17 (13) | 9 | **0.75** (N4) | 9/10 | 4 (+17), 28 | 12 s |
| iceberg-18196 | cross-module | 6 (3) | 2 (1) | 2 | 1.00 | 6/6 | **1** (N6), 0 | 9 s |
| zookeeper-2435 | dependency-framework | 38 (13) | 72 (0) | 67 | **0.46** (N5) | 6/22 | 5 (+5), 11 | 5 s |
| rocketmq-11090 | architectural | 18 (13) | 69 (21) | 41 | 0.92¹ | 17/18 | 2 (+4), 5 | 6 s |
| pulsar-26687 | large | 28 (14) | 301 (138) | 158 | 1.00 | 28/28 | 5 (+16), 30 | 13 s |
| iceberg-18195 | mixed-noisy | 12 (4) | 9 (4) | 8 | 0.75² | 5/6 | 1, 0 (N9) | 9 s |
| trino-31334 | complex-behavioral | 2 (1) | 2 (1) | 3 | 1.00 | 2/2 | 1 (+13), 13 | 22 s |
| elasticsearch-160228 | complex-behavioral | 5 (2) | 6 (2) | 6 | 1.00 | 4/4 | 1 (+23), 23 | 67 s |
| iceberg-18213 | complex-behavioral | 2 (1) | 7 (5) | 5 | 1.00 | 2/2 | 1, 0 (N9) | 8 s |
| resilience4j-2490 | complex-behavioral | 2 (1) | 12 (11) | 3 | 1.00 | 2/2 | 1 (+1), 1 | 3 s |
| pulsar-26456 | complex-behavioral | 2 (1) | 18 (6) | 15 | 1.00 | 2/2 | 1 (+6), 6 | 11 s |
| pulsar-26459 | closed-unmerged | 2 (1) | 9 (7) | 2 | 1.00 | 2/2 | 1 (+3), 3 | 11 s |
| redisson-7334 | closed-unmerged | 2 (1) | 3 (2) | 2 | 1.00 | 2/2 | 1, 0 | 5 s |
| zookeeper-2401 | closed-unmerged | 1 (1) | 1 (0) | 1 | 1.00 | 1/1 | 1 (+1), 1 | 5 s |
| iceberg-18169 | closed-unmerged | 7 (2) | 13 (6) | 13 | 1.00 | 7/7 | 3, 0 (N9) | 8 s |

¹ rocketmq-11090's `ConsumeMessageService` changes only Javadoc.
² iceberg-18195's `HttpClientProperties` changes only Javadoc links. Its build files are N7.

**The existing 66 entries:** no coverage change and no regression against `8823811`. They differ
only as #374–#377 intended:
- **#374, fewer cards:** kafka-23542 16 → 8, hibernate 98 → 86, keycloak 94 → 89, camel-26805 18 → 15, junit5 30 → 28, camel-26806 115 → 113, assertj 17 → 16, commons-io-866/872 −1/−2.
- **#375, no receiver swap repeated as an argument:** nacos-15628 and spring-petclinic-1913.
- **#376/#377, new rails:** kafka, spring-security, hibernate.

## Scores (What + Why + Impact, 0–12 each side)

The diff side was read and scored before Athena's output (protocol step 2). Effort was recorded
as the reading-effort proxy.

| Entry | Diff | Athena | vs. diff | Notes |
|---|---|---|---|---|
| pulsar-26707 | 11 | 5 | worse | `create: other statements changed ×3` hides the three `position = null` resets (N1); the test name `…DoesNotInheritPoisonedPosition` carries the why. |
| resilience4j-2507 | 11 | 7 | worse | `Builder#<init>: other statements changed` (N1); `fromShouldNotMutateBaseConfig` names the bug. |
| redisson-7343 | 8 | 4 | worse | `releaseAsync: return value changed` for a Lua rewrite (N2); only the test name hints at released permits. |
| redisson-7358 | 10 | **10** | equal | `Add time`/`timeAsync` on `RKeys`, `RKeysAsync`, Reactive and Rx: the feature at a glance. |
| rocketmq-11079 | 9 | **9** | equal | `Add shouldRecordValue`, the `suppressMinValueMetrics` accessors and `initLagAndDlqMetrics: branch added`. |
| trino-31311 | 9 | 4 | worse | Records read as removals of `equals`/`hashCode`/getters, with 45 misleading `Remove Capability` inferences (N3). |
| pulsar-26646 | 8 | 7 | within 1 | `Add getTransactionCoordinatorClient` on `PulsarClient` and the field type change. The annotation removal that *makes* it public is missing (N4). |
| iceberg-18196 | 10 | 8 | worse | `convert: +sourceData.getRowKind ×3` names the fix, but the Canvas shows one module out of three (N6). |
| zookeeper-2435 | 7 | **8** | **better** | The dependency folds name the Jetty EE10/jakarta migration better than 2k lines with LICENSE noise; seven signature-changing files are invisible (N5). |
| rocketmq-11090 | 9 | **10** | **better** | Pull-ups into `AbstractConsumeMessageService`, `setConsumeExecutor`, `SystemMessageConsumeExecutor` and the Proxy option tell the design. |
| pulsar-26687 | 8 | **8** | equal | The removed config fields and stats getters are there (`Remove field ServiceConfiguration#subscription…`, `Remove ConsumerStats#getKeyHashRanges`), but among 158 cards; the focus areas pick control-flow edits. |
| iceberg-18195 | 8 | 6 | worse | The `ApacheHttpClient.Builder → Apache5HttpClient.Builder` signatures are shown; the build dependency swap isn't (N7). |
| trino-31334 | 11 | 7 | worse | `finish: condition changed` understates a release-on-failure fix (N8); the test name carries it. |
| elasticsearch-160228 | 8 | 7 | within 1 | `Remove computeEdge`, the comparators, and `computeBounds: +min, +max, +vMin.greatCircleMinLatitude…` in both copies. |
| iceberg-18213 | 11 | **11** | equal | `runTaskWithRetry: branch added`, `Add causedByInterruption`, and tests named `…NotRetriedWhenFailureIsCausedByInterruption`. |
| resilience4j-2490 | 10 | **10** | equal | `refreshLimit: branch added` and `refreshSchedulerMustSurviveLimitDecreaseBelowAvailablePermits`. |
| pulsar-26456 | 8 | 7 | within 1 | `WriteInEventLoopCallback#run: branch added`, `Add releaseOpCmdAndRecycle` and `Add field writeEventLoop` outline the deferral; the ordering race needs the comments. |
| **Merged total** | **156** | **128** | | |
| pulsar-26459 | 9 | 7 | worse | Reason *hinted*: a composite buffer where `ByteBufPair` avoids one. |
| redisson-7334 | 9 | 4 | worse | Reason *invisible*: `return value changed` (N2). |
| zookeeper-2401 | 8 | 8 | equal | Reason *invisible*: the change is exact, but its futility lives outside the PR. |
| iceberg-18169 | 7 | 5 | worse | Reason *invisible*: a spec decision, not a code property. |
| **Closed total** | **33** | **24** | | |

## Prioritized next improvements

| # | Improvement | Entries affected | Why |
|---|---|---|---|
| 1 | **Name added or changed field assignments in body summaries** (N1): `+position = null`, `config = new …`. | 2 (+ likely common) | Small bug fixes are Athena's weakest category, and assignments are a common shape of them. |
| 2 | **Recognize a class-to-record conversion** (N3), and stop inferring `Remove Capability` for accessors a record replaces. | 1 | A correctness problem: 45 inferences claim capabilities were removed when nothing was. |
| 3 | **Report type-level annotation changes** (N4) and **package moves of same-named types** (N5). | 2 | Both hide API changes entirely; N5 alone leaves zookeeper-2435 at 0.46 coverage. |
| 4 | **Keep a fix repeated across parallel modules visible per module** (N6). | 1 | Backports across version modules are common in multi-version projects. |
| 5 | **Read Gradle dependency declarations and version catalogs** (N7), and **follow `projectDir` remapping** (N9). | 2+ | The Gradle equivalent of #340 and #376. |
| 6 | **Summarize a changed string-literal script** (N2) and **release-on-failure restructurings** (N8). | 3 | Lower priority: wording, not correctness. |

Unchanged from earlier reports: most rejection reasons and the *why* of small fixes need
consequence-level reasoning (an evidence-grounded AI layer, scored separately).

## Reproducing

```bash
evaluation/tools/run-corpus.sh          # all 87 entries; the new ones take 3–67 s each at 8a15ab4
evaluation/tools/coverage.py evaluation/snapshots/$(git rev-parse --short HEAD)/*
```

## Limitations

- **Single agent evaluator.** The same agent selected the PRs and scored both sides. The diff
  side was read and recorded before Athena's output, per the protocol, but blind or independent
  scoring of a sample would make the 82 % more trustworthy.
- **Batch mix.** Categories aren't balanced between batches, so compare ratios per category
  (small bug fixes: 53 %), not overall.
- **Reference understanding comes from PR descriptions.** redisson-7343's description describes
  an earlier approach than the merged diff; the merged code was scored.
- **Output, not UI.** Scores judge the JSON behind the Change Map, Explorer, Canvas and focus
  areas, not how it renders.
- **Keep this batch clean.** Once N1–N9 become tickets with these entries as definitions of done,
  the batch stops being out-of-sample, like the three before it.
