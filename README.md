# Athena

![Athena](athena.jpeg)

Athena analyzes a GitHub Pull Request's Java diff and groups it into
meaningful **Changes** — rename, move, extract, mechanical replacement,
signature change, and so on — instead of leaving a reviewer to
reconstruct that meaning from raw diff hunks.

## Status: MVP, library-first, one CLI entry point, an early web UI taking shape

Most of Athena today is a tested domain library — GitHub import, the
semantic detection engine, and view-model classes for review UI, review
context, and AI-assisted findings.

A **web UI** (Spring Boot REST API + a React/TypeScript/Vite/Tailwind
frontend, epic #71) is under active development and is meant to become
the primary way to use Athena. So far it covers connecting a GitHub
token and picking a repository/PR (ticket #73) — the rest of the review
loop (Change Map, drill-down, comments, review state, AI analysis) isn't
wired up yet.

There's also a read-only CLI (`com.athena.cli.Main`): point it at a real
PR and it prints a plain-text summary of the Changes it detects. It
exists to prove the pipeline works against real PRs and remains a
minimal secondary interface, not the product's eventual one.

## Try it

### Web UI

Requires Java 21, Maven, Node/npm, `git` on your `PATH`.

```bash
# Backend (from the repo root)
mvn spring-boot:run

# Frontend (in another terminal)
cd frontend && npm install && npm run dev
```

Open `http://localhost:7331` and connect with a GitHub Personal Access
Token. (The backend API runs on `http://localhost:7332`.)

### CLI

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
| `com.athena.git`            | Shared git-checkout utilities (shells out to `git`), used by both the CLI and the web backend |
| `com.athena.cli`            | The read-only CLI entry point — see "Try it" above |
| `com.athena` / `com.athena.web` | The Spring Boot web UI backend (REST API only, no server-rendered HTML) — see "Try it" above |
| `frontend/`                 | The React/TypeScript/Vite/Tailwind/TanStack Query web frontend — see `frontend/README.md` |

## Known limitations right now

- Java only — other languages in a PR are silently ignored.
- Read-only everywhere — no review state, comments, or GitHub sync yet, from either the CLI or the web UI.
- The web UI only covers connect → pick repository → pick PR so far; no Change Map, drill-down, comments, review state, or AI analysis wired up yet.
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
