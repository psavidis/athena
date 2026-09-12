# UI-First Experience

<!-- epic: #71 (UI-First Experience: Spring Boot + Web UI) — covers this whole document -->

## Context

`product-specification.md` §38–43 already describe the desired UI/UX
*behavior* (navigation model, review interaction, search/filtering), and
`visual-design-philosophy.md` describes the desired *feel* (calm,
intent-first, progressive disclosure). Neither says **how to actually
build and ship one**. Epic #5 (Review UI & Navigation) implemented §38–43
as headless Java view-model classes (`ChangeMapView`, `PrUnderstandingView`,
`ReviewQueue`, etc.) — correct as far as it went, but nothing renders
them. Today the only runnable thing in this codebase is a read-only,
plain-text CLI (`com.athena.cli.Main`, ticket #65).

This spec exists to close that gap: make a real, browser-based UI the
**primary way to use Athena** going forward.

## Vision

A Spring Boot application exposes the existing domain library — GitHub
import, the semantic engine, and the `reviewui`/`reviewcontext`/`ai`
view-models — over HTTP, and a browser-based frontend consumes it. A
reviewer authenticates with a GitHub token, picks a repository and PR,
and works the whole review loop (Change Map → drill-down → comments/
notes → review state → submission, optionally AI analysis) from the
browser. No existing domain logic changes — this is purely a new
application/web layer on top of what's already built and tested.

The existing CLI keeps working unmodified alongside this; it isn't being
replaced or deprecated by this spec.

## Scope boundary (MVP)

**In scope:**

- A Spring Boot backend module wrapping the existing `com.athena.github`,
  `com.athena.semantic`, `com.athena.reviewui`, `com.athena.reviewcontext`,
  and `com.athena.ai` packages, exposed as a **pure JSON REST API** — no
  server-side HTML rendering. It exists only to put the domain library on
  the network; all presentation lives in the frontend.
- A separate **React + TypeScript** frontend (its own project/toolchain:
  `Vite` build, `Tailwind CSS` for styling, `Framer Motion` for animation,
  `TanStack Query` for talking to the backend, a code/diff-highlighting
  library such as Shiki), covering the core loop: connect with a GitHub
  token → pick repo + PR → see the Change Map / PR Understanding View →
  drill down into a Change (Symbol/File/Diff/Line) → attach comments/
  private notes → set review state → submit the review. Optionally
  trigger AI analysis and evaluate findings.
- The user explicitly wants this **rich**: polished visuals, strong
  typography, and — as a later addition, not required for the first
  ticket that renders anything — animation that visualizes relationships
  between Changes (e.g. where a moved symbol came from). This is why a
  full frontend framework was chosen over server-rendered HTML: the
  ambition needs a real rendering/animation engine, not page reloads.
- Following `visual-design-philosophy.md`'s principles throughout —
  calm, intent-first hierarchy, progressive disclosure — expressed via
  this stack, not scaled back to fit a simpler one.

**Explicitly out of scope for this spec:**

- A richer terminal/`gh`-style CLI experience (connecting to a repo,
  selecting a PR, doing a review from the terminal) — deferred to a
  later phase by the user's own direction; the existing plain-text CLI
  is not being extended here.
- Multi-user auth/accounts beyond a single reviewer's own GitHub token,
  real-time collaboration, or any hosting/deployment concern beyond
  running locally for development.
- Any new domain/business logic. If the web layer needs something the
  domain model doesn't already provide, that's a signal to revisit this
  spec's scope, not to quietly grow business logic inside a controller.

## Success Criteria

- A user can start the Spring Boot app locally, authenticate with a
  GitHub token through the web UI, and pick a real repository and PR.
- The Change Map / PR Understanding View renders in the browser for that
  PR, backed by the existing, unmodified `TransformationDetector` +
  `ChangeGrouper`.
- A reviewer can drill down into a Change, attach a comment or private
  note, set its review state, and submit a review — all via the browser,
  wired to the already-built `reviewui`/`reviewcontext` classes without
  reimplementing their logic.
- AI analysis can be triggered and its findings reviewed from the same
  UI, wired to the already-built `AiAnalysisOrchestrator`/`AiFindingsBoard`.
- `com.athena.cli.Main` keeps working exactly as it does today — this
  adds a second interface, it doesn't touch the first.

## Open Questions

None blocking — all confirmed directly with the user: the technology
choice (Spring Boot REST API + React/TypeScript/Vite/Tailwind/Framer
Motion/TanStack Query frontend), the phasing (terminal/`gh`-style
experience deferred), and the frontend framework decision itself (a rich,
animatable UI needs a real frontend framework, not server-rendered HTML).
Exact endpoint/page shapes remain ordinary `plan-feature` decomposition
decisions, not spec ambiguities.

## Links

- `docs/specs/product-specification.md` §38–43 (User Interface, Primary
  Navigation, Review Interaction, Review Completion)
- `docs/specs/visual-design-philosophy.md` (visual/interaction principles
  to follow, at MVP simplicity)
- Epic #5 (Review UI & Navigation) — the view-model classes this UI
  renders already exist there
