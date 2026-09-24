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
| `reports/` | Dated evaluation reports, written against a specific snapshot directory. The first is `reports/2026-09-23-baseline-a5bb763.md`; the re-run after #260–#271 is `reports/2026-09-24-rerun-80ce13f.md`; the third run, after #285–#296, is `reports/2026-09-24-rerun-e2b2780.md`; the baseline for the 16 entries added in #311 is `reports/2026-09-24-expansion-baseline-c6a1721.md`. |

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

Candidates considered and kept in reserve: `keycloak-53012` (cross-module bug fix),
`mockito-3792` (Android mock maker swap, mostly Gradle/Kotlin), `spring-petclinic-2279`
(dependency bump mixed with test renames), `jackson-databind-6213` (deferred
deserialization work), `logback-1060` (caller-data extraction in async appenders),
`hibernate-orm-13521` (interceptor calls for stateless sessions). Closed PRs left out:
abandoned or administrative closes (for example `logback-1030`, which moved to another
repository, and `jackson-databind-6153`, which was retargeted).
