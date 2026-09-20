# Athena

> Code review with context.


![Athena logo](athena-logo-white.png)

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
mvn spring-boot:run -pl athena-app

# Frontend (in another terminal)
cd frontend && npm install && npm run dev
```

Open `http://localhost:7331` and connect with a GitHub Personal Access
Token. (The backend API runs on `http://localhost:7332`.)

#### Optional: `athena.localhost` instead of a bare port

Local dev machines here also route Athena through
`http://athena.localhost/` (no port to remember) via a shared
[Caddy](https://caddyserver.com) reverse proxy running on port 80, alongside
other local apps under their own `*.localhost` names (e.g.
`poiesis.localhost`). This is machine-level dev environment config, not
part of the repo:

- `*.localhost` hostnames always resolve to `127.0.0.1` per RFC 6761 — no
  `/etc/hosts` edit needed, and no ambiguity with a real domain or with
  `.local`'s mDNS/Bonjour reservation.
- Install Caddy once via Homebrew: `brew install caddy`.
- Caddy listens on port 80 and reverse-proxies by hostname to each app's
  real dev-server port — `athena.localhost` → `localhost:7331`,
  `poiesis.localhost` → `localhost:8000`, and so on — configured in
  `~/.config/caddy/Caddyfile` (symlinked from Homebrew's default
  `/opt/homebrew/etc/Caddyfile` so `brew services` manages it as one
  `launchd` daemon: `sudo brew services start caddy`).
- To add another app, append a block to that Caddyfile
  (`name.localhost { reverse_proxy localhost:PORT }`) and reload with
  `sudo caddy reload --config /opt/homebrew/etc/Caddyfile` — no restart
  needed.

This is purely a convenience layer over the `localhost:7331` URL above;
either works identically.

### CLI

Requires Java 21, Maven, `git` on your `PATH`, and a GitHub token with
read access to the target repo.

```bash
# Build once (rebuild the classpath file if dependencies change)
mvn -q -pl athena-app -am compile dependency:build-classpath -Dmdep.outputFile=/tmp/athena-cp.txt

# Run against any real PR
GITHUB_TOKEN=$(gh auth token) java -cp "athena-app/target/classes:$(cat /tmp/athena-cp.txt)" \
  com.athena.cli.Main <owner/repo> <pr-number>
```

`GITHUB_TOKEN` is read from the environment only — never pass it as a
command-line argument.

## Run the tests

```bash
mvn test
```

Tests are Cucumber (`.feature` files under each module's own
`src/test/resources/features/`) plus their Java step definitions — see
`CODE_STYLE.md` for the testing philosophy (Detroit-school/classicist;
mocks only at genuine external boundaries).

`WhisperXTranscriptionProviderTest` and the WhisperX-backed Cucumber
scenarios (`whisperx_transcription_provider.feature`, ticket #251) need a
one-time local setup, and are skipped (not failed) when it's absent:

- A `python3.12` interpreter on `PATH` — WhisperX's pinned `ctranslate2`
  dependency has no wheel for Python 3.14+, so a newer system default
  Python will not work.
- A Hugging Face access token with access to the gated diarization
  model(s) the installed WhisperX version requests (confirm exactly which
  by running the script once — this has changed between WhisperX
  releases; do not assume the model this ticket's own history names is
  still current). Create a free account at huggingface.co, accept the
  model's access conditions on its model page, generate a read-scoped
  token at huggingface.co/settings/tokens, and either place it at
  `~/.cache/huggingface/token` or set `HF_TOKEN`/`HUGGINGFACE_HUB_TOKEN`.
- Verified on macOS; expected (not independently verified) to work on
  Ubuntu too, since the underlying stack (Python/PyTorch/CTranslate2) has
  no macOS-specific dependency — CPU-only inference is the baseline on
  both.

## Module layout

The detection engine is a plugin architecture: `athena-core` defines a
`LanguagePlugin`/`FrameworkPlugin` SPI (`com.athena.semantic.spi`,
discovered via `java.util.ServiceLoader`) and owns the language-agnostic
domain model; each language or framework is a separate module implementing
that SPI, with no compile-time dependency back from core. Java
(`athena-plugin-java`, via JavaParser) and Spring/JPA/Jackson/JUnit
(`athena-plugin-spring`) are the first implementations — adding a new
language or framework is a new sibling module, not a change to core or the
app.

| Module                | What it's for                                                                 |
|------------------------|--------------------------------------------------------------------------------|
| `athena-core`          | The `LanguagePlugin`/`FrameworkPlugin` SPI, the language-agnostic domain model (`Change`, `Taxonomy`, `ReviewState`, ...), and `PrAnalyzer`'s orchestration — no JavaParser or Spring dependency |
| `athena-plugin-java`   | The Java `LanguagePlugin`: parses Java via JavaParser, detects transformations (rename/move/extract/...), builds the symbol model |
| `athena-plugin-spring` | The Spring `FrameworkPlugin`: recognizes Spring/JPA/Jackson/JUnit annotation conventions for the FRAMEWORK classification dimension |
| `athena-app`           | The Spring Boot web UI backend + CLI entry point; bundles the plugin jars on its runtime classpath — see "Try it" above |
| `frontend/`            | The React/TypeScript/Vite/Tailwind/TanStack Query web frontend — see `frontend/README.md` |

Within `athena-app`, packages are organized the same way as before:
`com.athena.github` (GitHub import/sync), `com.athena.reviewui` (review UI
view-models), `com.athena.reviewcontext` (Review Context assembly +
continuity), `com.athena.ai` (optional post-review AI analysis),
`com.athena.git` (shared git-checkout utilities), `com.athena.cli` (the CLI
entry point), `com.athena` / `com.athena.web` (the Spring Boot backend),
and `com.athena.plugins` (`ServiceLoader` discovery wiring for the plugin
modules above).

## Known limitations right now

- Java is the only `LanguagePlugin` implemented so far — other languages in a PR are silently ignored until a matching plugin module exists.
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

## Artwork

![Athena](athena.jpeg)

---

**Keeping this current:** when a PR changes what's true here — a new
package, a limitation that's now fixed, a new way to run something — update
the relevant section above in the same PR. Keep entries short; one line
per fact.
