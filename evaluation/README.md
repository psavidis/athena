# Athena evaluation corpus

A growing set of real, public GitHub pull requests that Athena is evaluated against. It
lets us say "after this change, Athena handled these PRs differently" with evidence,
instead of judging from a single PR (issue #258).

| Path | What it is |
|---|---|
| `corpus.tsv` | The PRs, pinned to exact base/head SHAs, one row per entry. |
| `PROTOCOL.md` | How an entry is evaluated: procedure, scoring scale, metrics, report template. |
| `tools/run-corpus.sh` | Snapshots Athena's representation of every entry (or the ids you pass) into `snapshots/<athena-sha>/<id>/`. |
| `tools/AthenaSnapshot.java` | Runs Athena's real web controllers in-process against one entry and writes their JSON responses. Called by `run-corpus.sh`. |
| `tools/coverage.py` | Derives `metrics.json` (diff-vs-Athena coverage) for a snapshot. |
| `tools/summarize.py` | Prints a snapshot as plain text: Change Map, Explorer cards, Canvas, focus areas. Used for the protocol's Athena pass. |
| `snapshots/<athena-sha>/` | Raw evidence per Athena version: `pr.diff` plus every view Athena would render. |
| `reports/` | Dated evaluation reports, written against a specific snapshot directory. The first is `reports/2026-09-23-baseline-a5bb763.md`; the re-run after #260–#271 is `reports/2026-09-24-rerun-80ce13f.md`; the third run, after #285–#296, is `reports/2026-09-24-rerun-e2b2780.md`; the baseline for the 16 entries added in #311 is `reports/2026-09-24-expansion-baseline-c6a1721.md`; the 26-entry run after #313–#320 is `reports/2026-09-24-rerun-9d1b462.md`; the baseline for the 19 entries added in #332 is `reports/2026-09-24-expansion2-baseline-b6f6f49.md`; the 45-entry run after #334–#340 is `reports/2026-09-25-rerun-feaac14.md`; the baseline for the 21 entries added in #355 is `reports/2026-09-25-expansion3-baseline-61d70d2.md`; the 66-entry run after #357–#363 is `reports/2026-09-25-rerun-8823811.md`; the baseline for the 21 entries added in #382 is `reports/2026-09-25-expansion4-baseline-8a15ab4.md`. |

## Re-running against a newer Athena

```bash
evaluation/tools/run-corpus.sh              # all entries; or pass ids, e.g. gson-3101
evaluation/tools/coverage.py evaluation/snapshots/$(git rev-parse --short HEAD)/*
```

Requirements:

- Java 21, Maven, `git`.
- `gh`, authenticated. It's only used to download the public PR diff with a GET.
- Network access to github.com.

Athena itself gets no GitHub token. It fetches each pinned revision anonymously
(`git fetch --depth 1`), so the run can't write to the evaluated PRs.

Large repositories take minutes per entry. Athena parses every Java file in both
revisions, not only the changed ones. That cost is itself one of the baseline findings.

Then compare the new `metrics.json` files with the previous snapshot directory's, re-read
the entries whose numbers moved, and write a new report under `reports/` using
`PROTOCOL.md`'s template.

## Current corpus

| Id | Category | Why this PR |
|---|---|---|
| `commons-lang-1794` | small-bug-fix | 2-line fix inside one method body plus a test. Tests whether a localized behavioral fix is visible at all. |
| `spring-boot-51774` | simple-feature | New configuration property: field and accessors on a `@ConfigurationProperties` class plus one line of wiring. The feature's meaning lives in a one-line mapping. |
| `gson-3101` | refactoring | If-chains rewritten as `switch` statements across 8 methods of one class. Pure control-flow refactoring, no signature changes. |
| `mockito-3835` | api-change | New `@Spy(mockMaker=…)` annotation attribute, plus duplicated spy-creation logic extracted from two classes into a new `SpyAnnotationUtil`. |
| `spring-boot-51490` | cross-module | Same Spring-framework migration (`BeanFactoryAware` setter injection → constructor injection in `@Import` participants) across 6 modules, plus a new ArchUnit rule enforcing it. |
| `mockito-3784` | dependency-framework | New JSpecify dependency: build file, `module-info`, and `@Nullable` on public API parameters. |
| `jackson-databind-6209` | architectural | Removes an internal abstraction (`UnreflectHandleSupplier`) and inlines lazy `volatile MethodHandle` fields into 3 classes. A responsibility restructuring for memory and performance. |
| `keycloak-52898` | large | 22 files: new client-policy executor and factory, new SPI events, a token-response context class hierarchy pulled up into two new abstract bases, and 3 large tests. |
| `spring-petclinic-1913` | mixed-noisy | Spring Boot 3.5 upgrade: 29 copyright-only Java files, HTML/CSS cleanup, build files, and one real responsibility move (`findPetTypes` from `OwnerRepository` to a new `PetTypeRepository`). |
| `spring-framework-37268` | complex-behavioral | A 3-line guard in a lock-free cache that fixes permanent size drift under a race. A tiny diff with large, non-obvious consequences. |
| `commons-io-860` | small-bug-fix | `BoundedReader.skip` counted the *requested* skip, not the actual one, and could skip past the bound. A small accounting fix in one method, plus regression tests. |
| `logback-1051` | simple-feature | `HALF_DAY` periodicity existed but could never be selected. The fix inserts it into an ordered list and adds `switch` branches in `RollingCalendar`. The feature is a one-line list change plus two cases. |
| `guava-8663` | refactoring | Recursion → iteration in `MultiReader`, mirrored in guava's `android/` flavour. A behavior-preserving rewrite that removes a stack-overflow risk. |
| `netty-17594` | api-change | New static `PropertyKey.newKey()`, not tied to a connection; `DefaultStream` dispatches on the key type (array fast path vs lazy `IdentityHashMap`). |
| `junit5-6057` | cross-module | Nanosecond timeout measurement and a new upper bound on timeouts, across `junit-jupiter-api`, `-engine`, tests and docs. Kotlin-DSL Gradle build. |
| `commons-io-872` | dependency-framework | Calls into `sun.*` internals for byte-buffer cleaning are made optional. The maintainer's re-do of `commons-io-866`. |
| `guava-8647` | architectural | Removes view caching across `common.collect`, including the whole `ViewCachingAbstractMap`, in both the `guava/` and `android/` flavours. 82 files. |
| `hibernate-orm-13477` | large | HHH-20905: `@Filter` and `@SQLRestriction` on to-one associations. 34 files, +2.6k lines, most of it tests. The PR description is the template; the meaning is in the title and the Jira key. |
| `guava-8651` | mixed-noisy | IntelliJ-driven `final` sweep over 148 files, with a few real visibility changes (`LineBuffer` methods to package-private) hidden in the noise. |
| `netty-17589` | complex-behavioral | A race between executor suspension and scheduled-task cancellation forced an unrequested shutdown. A small state-machine fix in `SingleThreadEventExecutor` plus a regression test. |
| `gson-3086` | refactoring | Caches a successful reflective accessibility check per `BoundField` with an `AtomicBoolean`. The simpler design the reviewer asked for in `gson-3054`. |
| `commons-lang-1790` | closed-unmerged | A competing fix for LANG-1834, the bug `commons-lang-1794` fixed. Closed as superseded by #1794 after the author stopped responding. The pair shows two fixes for one bug. |
| `commons-io-866` | closed-unmerged | Check `sun.misc.Unsafe` access on Java 23+. The review found a regression: the `FileChannel` leaks if cleaning fails. The maintainer re-did it as #872 (`commons-io-872`). |
| `mockito-3843` | closed-unmerged | Restores multi-classloader lookup for subclass mocks (OSGi). Rejected on design: the lookup was removed on purpose, because restoring it loses package-private mockability. |
| `jackson-databind-6163` | closed-unmerged | 4× loop unrolling of `_serializePropertiesFiltered`. Not merged: the maintainer's review measured a larger method bytecode, suspected it makes things slower, and asked for a benchmark. |
| `gson-3054` | closed-unmerged | A per-field `AccessibleCache` class. Closed when the fork was deleted; the reviewer had already asked for a simpler design, merged as #3086 (`gson-3086`). |
| `commons-collections-716` | small-bug-fix | `TreeList` add/remove incremented `modCount` before the bounds check, so a rejected call still invalidated iterators. A statement-order fix. |
| `dubbo-16350` | small-bug-fix | `StringUtils.substringAfter` returns an empty string when the separator is absent. A tiny return-value fix. |
| `dubbo-16299` | small-bug-fix | A clear exception when a request parameter isn't `Serializable`, instead of a misleading NPE. The merged pair of `dubbo-16298`. |
| `error-prone-6093` | simple-feature | A new check flagging `Class.forName(...)` compared with `null`. A self-contained new class plus its test. |
| `spring-security-19696` | simple-feature | Modernizes the default `RequestCache` list of background requests to ignore, across `web` and `config`. Constant-list and matcher changes. |
| `error-prone-6105` | refactoring | Check APIs move to annotation mirrors, with centralized annotation names, across `check_api` and `core`. |
| `error-prone-6125` | api-change | Adds an `allowedPaths` element to `@RestrictedApi` and `@RestrictedInheritance`, and enforces it. |
| `maven-13229` | cross-module | Max-thread configuration across `api/maven-api-cli`, `impl/maven-cli` and `compat/maven-embedder`. |
| `dubbo-16313` | dependency-framework | Spring Boot integration: `addOrReplace` must not throw on an immutable property source. |
| `dropwizard-11325` | dependency-framework | Dependency-tree cleanup across 29 `pom.xml` files, with no Java at all. Tests how Athena presents a change it can't model. |
| `vertx-6367` | architectural | Shared servers are reimplemented on the shared-resource feature (10 files, ±400 lines in `vertx-core`). |
| `lucene-16696` | large | The HNSW merge scorer stops reading merged float vectors back (9 files, +1.7k lines). A large performance change. |
| `vertx-6339` | mixed-noisy | `ServiceResource` and `CleanableObject` exposed as internals across 13 files. Visibility and package moves amid small edits. |
| `vertx-6352` | complex-behavioral | Interrupting a suspended virtual thread must leave its task queue consistent. A concurrency state fix. |
| `micronaut-13421` | complex-behavioral | Releases the connection when a streaming client call fails with an error status. A resource-leak fix. |
| `vertx-6308` | closed-unmerged | Host names in DNS client setup. Not merged: it relied on a blocking Java resolution API; the maintainer re-did it lazily with the Vert.x name resolver. |
| `vertx-6303` | closed-unmerged | `CleanableObject.shutdown()` returning null after GC. Not merged: without a test it was a speculative fix, and a different fix with a test was merged. |
| `dubbo-16298` | closed-unmerged | A clear error for non-`Serializable` parameters. Not merged: the reviewer saw a risk of swallowing other exceptions and couldn't reproduce the NPE; `dubbo-16299` was preferred. |
| `commons-collections-674` | closed-unmerged | Null-key checks in `ConcurrentReferenceHashMap`. Not merged: the maintainer committed a different, better solution. |
| `jenkins-27381` | small-bug-fix | `QueryParameterMap` read `kv[1]` unconditionally, so `?flag` or `?rev=` threw `ArrayIndexOutOfBoundsException`. A one-method guard; Maven. |
| `kafka-23538` | small-bug-fix | An inverted ternary swapped the `offset-commit`/`txn-offset-commit` labels in a fencing log. The smallest possible fix: a swapped condition. Gradle. |
| `nacos-15873` | small-bug-fix | After a full cluster restart, connection-based clients were in the distro snapshot and lost their instances for good. Skipping them is a two-line guard with a large consequence. |
| `testcontainers-11970` | simple-feature | `AzuriteContainer.withCommandOptions(...)`, because `configure()` overwrote any `withCommand` flags. A new public method on a builder; Gradle. |
| `camel-26831` | simple-feature | A REST producer fails with the unresolved path parameter's name instead of sending `%7Bsku%7D` and getting a 404. A new check plus message. |
| `assertj-4338` | refactoring | Reset of the global AssertJ configuration made consistent across 30 files, replacing an extension that couldn't work without runtime retention. Wide, repetitive test refactoring. |
| `nacos-15854` | refactoring | Removes the legacy `GetRequest`/`Log` fallback from Raft log parsing and keeps the parse error. The maintainers' follow-up to `nacos-15628`. |
| `quarkus-56904` | api-change | `char[]` overloads for `BcryptUtil.bcryptHash`/`matches` and deprecation of the `String` ones, so passwords can be zeroed. An additive API with deprecations. |
| `camel-26805` | cross-module | The inflight, blocked and shutdown views report the node's source line, reading a value core already stored. Changes in core, management and dev consoles. |
| `nacos-15856` | dependency-framework | Datasource dialect plugins provide their default JDBC driver, with the config property as an override. A plugin SPI extension across modules and properties files. |
| `jetty-15668` | architectural | Failure handling for connections accepted, not only connected: `ManagedSelector` tasks become `Closeable` and are closed on failure. A lifecycle rework in the selector layer. |
| `camel-26806` | large | About 45 bugs found in a review of the simple language, one commit per sub-task, across 58 files. |
| `openrewrite-8915` | mixed-noisy | Write `services.gradle.org` distribution URLs again, as `gradle wrapper` does: a small logic change inside many test-resource and fixture edits. Gradle Kotlin DSL. |
| `jetty-15648` | complex-behavioral | The FCGI application task is dispatched only after the parser returns, so an inline task can't release the input buffer mid-parse. A concurrency fix by reordering. Supersedes `jetty-15616`. |
| `camel-26818` | complex-behavioral | A parallel streaming split with a trailing null part completed before its running parts finished. A completion-accounting fix in the Splitter. |
| `kafka-23570` | complex-behavioral | `consumedOffsets` survived a task's wipe-and-revive under EOS, so a wiped `KTable` store looked fully restored. One line in `close()` plus a long test. The root-cause fix `kafka-23339` didn't find. |
| `kafka-23542` | complex-behavioral | A static member rejoining with epoch 0 was fenced by its own instance id. A coordinator state-machine fix with 1.2k lines of tests. |
| `kafka-23339` | closed-unmerged | Retries a missing committed offset during restore. Not merged: the reviewer was "still worried" the root cause wasn't understood; `kafka-23570` fixed the actual cause (stale `consumedOffsets`). |
| `nacos-15628` | closed-unmerged | Titled "add logging", it also removed the legacy parse fallbacks and a test dependency. Not merged: breaking changes beyond its stated scope. The maintainers re-did it deliberately as `nacos-15854`. |
| `jetty-15616` | closed-unmerged | A per-connection lock around FCGI input handling. Not merged: the reviewer called it a coarse lock that masks the race rather than a proper fix. Superseded by `jetty-15648`. |
| `camel-26819` | closed-unmerged | Concurrent bean registration made thread-safe with `ConcurrentHashMap` and a lock. Not merged: the review found a remaining reader-side race and a symptom-masking null guard. |
| `pulsar-26707` | small-bug-fix | A recycled `EntryImpl` could keep a stale `(-1, -1)` position cached by a late `getPosition()`; `create()` now resets it. Three one-line field resets; Maven. |
| `resilience4j-2507` | small-bug-fix | `ThreadPoolBulkheadConfig.from(base)` kept a reference to `base`, so building a derived config mutated it. A constructor that copies fields; Gradle. |
| `redisson-7343` | small-bug-fix | `release()` left released permits in the rate limiter's sorted set, so they were counted again on expiry. A Lua script inside a Java string. The root-cause fix `redisson-7334` didn't make. |
| `redisson-7358` | simple-feature | `RKeys.time()` returning the Redis server time, across the sync, async, Reactive and Rx interfaces. An additive API over four facades. |
| `rocketmq-11079` | simple-feature | A broker flag that stops exporting lag, in-flight and available metrics whose value is zero. The same guard wrapped around eight gauge callbacks. |
| `trino-31311` | refactoring | Nine value classes become records, and every `getX()` call becomes `x()` across 35 files. A language-level refactoring with no behavior change. |
| `pulsar-26646` | api-change | `TransactionCoordinatorClient` loses `@InterfaceAudience.Private` and becomes reachable from `PulsarClient`. An API promoted to public, mostly by an annotation. |
| `iceberg-18196` | cross-module | A one-line `RowKind` fix backported to three Flink version modules (1.20, 2.1, 2.2), with the same test in each. The same change in parallel modules. |
| `zookeeper-2435` | dependency-framework | Jetty 9.4 → 12.1 (EE10): `javax` → `jakarta` across 17 files, four admin/metrics classes reworked, one deleted, plus LICENSE and OWASP noise. Maven. |
| `rocketmq-11090` | architectural | Push consumers accept an external consume executor, extracted into a new `AbstractConsumeMessageService`; the Proxy's system consumers share one pool. |
| `pulsar-26687` | large | PIP-379 cleanup: deletes the classic Shared/Key_Shared dispatchers, their two config flags and two deprecated stats fields. About 4k deleted lines. |
| `iceberg-18195` | mixed-noisy | AWS SDK bump with a switch to the Apache HttpClient 5 client: a two-class type swap among LICENSE, runtime-deps, version-catalog and docs edits. Gradle. |
| `trino-31334` | complex-behavioral | `PartitionedOutputOperator.finish()` returns the partitioner to its pool even when the flush fails, using a `Closer`. A resource leak on an error path. |
| `elasticsearch-160228` | complex-behavioral | H3 cell bounds checked only one of the two edges at the extreme vertex. The same geometric fix in two copies of the utility. Gradle, very large repository. |
| `iceberg-18213` | complex-behavioral | `Tasks` retried work whose failure was caused by an interrupt. It now walks the cause chain and restores the interrupt flag. Cancellation semantics. |
| `resilience4j-2490` | complex-behavioral | Lowering a semaphore rate limiter's limit made `refreshLimit()` release a negative count, which killed the scheduled refresh for good. A two-branch fix. |
| `pulsar-26456` | complex-behavioral | The producer's send-timeout path released a frame still queued for writing on the event loop. Release and recycle are now deferred to that loop. The fix `pulsar-26459` should have been. |
| `pulsar-26459` | closed-unmerged | Writes each frame as one `CompositeByteBuf` to avoid header-only frames. Not merged: `ByteBufPair` exists to avoid composite buffers, and the reviewers found Netty misused on the release side, which `pulsar-26456` fixed. |
| `redisson-7334` | closed-unmerged | Clamps available permits to the rate after released permits expire. Not merged: a symptom clamp; the maintainer fixed the root cause in `redisson-7343` instead. |
| `zookeeper-2401` | closed-unmerged | Constant-time comparison for the session password. Not merged: the password is derivable from the session id anyway, so it's no real security gain. |
| `iceberg-18169` | closed-unmerged | Restores the pre-#16765 compact geospatial type strings. Not merged: after a dev-list discussion the maintainers kept the explicit form as the better behavior, to be documented. |

Candidates considered and kept in reserve: `keycloak-53012` (cross-module bug fix),
`mockito-3792` (Android mock maker swap, mostly Gradle/Kotlin), `spring-petclinic-2279`
(dependency bump mixed with test renames), `jackson-databind-6213` (deferred
deserialization work), `logback-1060` (caller-data extraction in async appenders),
`hibernate-orm-13521` (interceptor calls for stateless sessions). Closed PRs left out:
abandoned or administrative closes (for example `logback-1030`, which moved to another
repository, and `jackson-databind-6153`, which was retargeted).
