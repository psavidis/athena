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
| `reports/` | Dated evaluation reports, written against a specific snapshot directory. The first is `reports/2026-09-23-baseline-a5bb763.md`. |

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

Candidates considered and kept in reserve: `keycloak-53012` (cross-module bug fix),
`mockito-3792` (Android mock maker swap, mostly Gradle/Kotlin), `spring-petclinic-2279`
(dependency bump mixed with test renames), `jackson-databind-6213` (deferred
deserialization work), `junit5-6057` (sub-millisecond timeouts). `junit-team/junit5` and
`spring-projects/spring-data-jpa` were also surveyed, but their recent merged PRs were
mostly docs or bots, so they aren't represented yet.
