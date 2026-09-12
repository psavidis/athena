# Athena

Athena analyzes a GitHub Pull Request's Java diff and groups it into
meaningful **Changes** — rename, move, extract, mechanical replacement,
signature change, and so on — instead of leaving a reviewer to
reconstruct that meaning from raw diff hunks.

## Status: MVP, library-first, one CLI entry point

Most of Athena today is a tested domain library — GitHub import, the
semantic detection engine, and view-model classes for review UI, review
context, and AI-assisted findings. There is **no web or IDE UI yet**; the
`reviewui`/`reviewcontext`/`ai` packages are consumed only by the test
suite so far.

The one runnable, end-to-end thing that exists is a read-only CLI
(`com.athena.cli.Main`): point it at a real PR and it prints a plain-text
summary of the Changes it detects. That's it — no review state, no
comments, nothing synced back to GitHub. It exists to prove the pipeline
actually works against real PRs, not as the product's eventual interface.

## Try it

Requires Java 21, Maven, `git` on your `PATH`, and a GitHub token with
read access to the target repo.

```bash
# Build once (rebuild the classpath file if dependencies change)
mvn -q compile dependency:build-classpath -Dmdep.outputFile=/tmp/athena-cp.txt

# Run against any real PR
GITHUB_TOKEN=$(gh auth token) java -cp "target/classes:$(cat /tmp/athena-cp.txt)" \
  com.athena.cli.Main <owner/repo> <pr-number>
```

`GITHUB_TOKEN` is read from the environment only — never pass it as a
command-line argument.

## Run the tests

```bash
mvn test
```

Tests are Cucumber (`.feature` files under `src/test/resources/features/`)
plus their Java step definitions — see `CODE_STYLE.md` for the testing
philosophy (Detroit-school/classicist; mocks only at genuine external
boundaries).

## Package layout

| Package                    | What it's for                                                             |
|-----------------------------|----------------------------------------------------------------------------|
| `com.athena.github`         | Importing a PR's metadata/diff from GitHub, and syncing comments/decisions back |
| `com.athena.semantic`       | The detection engine: parses Java, detects transformations, groups them into Changes |
| `com.athena.reviewui`       | Review UI view-models (Change Map, drill-down, search) — not yet rendered anywhere |
| `com.athena.reviewcontext`  | Assembling a reviewer's session into a Review Context artifact + continuity across revisions |
| `com.athena.ai`             | Optional post-review AI analysis: context boundary, provider abstraction, findings review |
| `com.athena.cli`            | The one executable entry point — see "Try it" above |

## Known limitations right now

- Java only — other languages in a PR are silently ignored.
- Read-only — no review state, comments, or GitHub sync from the CLI.
- No UI beyond the CLI's plain-text output.
- A record's component list changing is detected; a whole record type
  being added/removed, or a type being *renamed*, is not yet.
- No pagination/performance handling for very large PRs.

## Where to look next

- [`CLAUDE.md`](CLAUDE.md) — the ticket-driven workflow this project follows
- [`CODE_STYLE.md`](CODE_STYLE.md) — the one style guide (design + testing)
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — how to propose and submit a change
- [`docs/specs/product-specification.md`](docs/specs/product-specification.md) — the product spec this was built from

---

**Keeping this current:** when a PR changes what's true here — a new
package, a limitation that's now fixed, a new way to run something — update
the relevant section above in the same PR. Keep entries short; one line
per fact.
