# Athena PR evaluation protocol

The procedure every corpus PR is evaluated with (issue #258). It exists so that a later
evaluation of a newer Athena can be compared against an earlier one entry by entry
("after this change, Athena handled these PRs differently") instead of by impression.

The protocol has two layers:

1. **Mechanical layer**: re-computed by tools and diffable between Athena versions.
   `tools/run-corpus.sh` captures Athena's representation. `tools/coverage.py` derives
   metrics from it.
2. **Judgement layer**: a human (or agent) reviewer's reading, recorded in a report
   under `reports/` using the template below.

## 0. Ground rules

- **Read-only toward the evaluated repositories.** Nothing may be written to the PRs
  or their repositories: no comments, reviews, resolved threads or "viewed" marks. The
  snapshot tool never gives Athena a GitHub token. Athena fetches public revisions
  anonymously. If the web UI is used to look at a PR, use only the read views.
- **Evaluate what exists.** Record Athena's current behavior, including gaps. Don't
  tune Athena to the corpus: the corpus measures the product; it isn't a test fixture.
  If a product change is motivated by a corpus finding, it still needs its own ticket.
- **This isn't a model benchmark.** Athena's AI features (review briefing summary,
  uncertainty questions, module narratives) are excluded from the baseline because they
  depend on an external model and a key. Record them separately if evaluated, and never
  mix them into the deterministic scores.

## 1. Corpus

`corpus.tsv` lists each entry with the exact `base_sha` and `head_sha` it was evaluated
at:

- `base_sha` is the merge-base of the PR's base branch and its head, which is what
  GitHub's "Files changed" tab diffs against.
- `merge_sha` is recorded for reference only.

Pinned SHAs keep an entry stable even after the PR's branch is deleted or its base
branch moves.

**Selection criteria:**

- Public, merged, primarily Java. Athena only has a Java language plugin and a Spring
  framework plugin today.
- One entry per change category (below), so recurring themes can be told apart from
  one-off quirks.
- A real change, not a bot bump or a docs-only change. Such PRs say nothing about
  understanding code.
- Understandable from the PR itself plus its description. Avoid PRs whose meaning lives
  in a long off-GitHub discussion.

**Categories:** `small-bug-fix`, `simple-feature`, `refactoring`, `api-change`,
`cross-module`, `dependency-framework`, `architectural`, `large`, `mixed-noisy`,
`complex-behavioral`.

**Growing the corpus:**

- Append rows. Never edit an existing row's SHAs, because that silently invalidates
  every earlier result for it.
- A new entry needs a category and a one-line rationale in `README.md`.
- Add a second or third entry per category before adding exotic categories.

## 2. Procedure (per PR)

Do the steps in this order. Order matters: the first reading of a PR anchors every
later one.

1. **Snapshot.** Run `tools/run-corpus.sh <id>` then `tools/coverage.py
   snapshots/<athena-sha>/<id>`. This writes:
   - `pr.diff`: the conventional GitHub diff.
   - `change-map.json`: the Change Map with categories and class groups, exactly as
     `/api/review/change-map` serves it.
   - `semantic-profile.json`: the PR-level Semantic Change Explorer view, as
     `/api/review/semantic-profile`.
   - `module-profiles.json`: the Explorer for each module.
   - `topology.json`: the Semantic Canvas territory map, as `/api/review/topology`.
   - `unrepresented-files.json`: the changed files no Change represents, and why, as
     `/api/review/unrepresented-files` (available from Athena versions with #260).
   - `focus-areas.json`: the deterministic part of the Review Briefing.
   - `changes.json`: each Change's detail (kind, files, symbols).
   - `run.json`: timings and counts.
   - `metrics.json`: coverage metrics (section 4).
2. **Diff pass.** Read `pr.diff` only, not the PR description, and not Athena's
   output. Write *Diff understanding*: what changed, why (as far as the code alone
   shows), and what else it can affect. Note what you had to reconstruct by jumping
   between files.
3. **Athena pass.** Read the Athena snapshot. If an entry needs its evidence, look at
   the evidence Athena itself attaches to that entry, but don't go back to the full
   diff. Write *Athena understanding* the same way.
4. **Reference.** Read the PR description and linked issue. Write the *Reference
   understanding*: the intent and impact a well-informed reviewer should come away
   with. Everything is scored against this.
5. **Score** (section 3), then record observations and at least one gap analysis
   (section 5).

## 3. Scoring

Score both the diff and Athena against the reference understanding, from 0 to 4, on
three axes:

| Axis | Question |
|---|---|
| **What** | Does the reader know which code changed and how, structurally? |
| **Why** | Does the reader know the intent: the bug, the feature, the refactoring goal? |
| **Impact** | Does the reader know the consequences: behavior, callers, contracts, framework, risk? |

**Scale:**

- **0**: nothing, or misleading.
- **1**: fragments; the reader would likely draw a wrong conclusion.
- **2**: partial; the reader knows something happened but must reconstruct the rest.
- **3**: mostly there; small gaps.
- **4**: complete for review purposes.

Every score needs a one-line explanation. A bare number is not a result. Scores are an
evaluation aid for spotting patterns, not a product KPI. Don't average them across PRs
in a headline.

**Effort.** The ticket asks for "time with diff vs. time with Athena". Wall-clock time
is only meaningful for a human evaluator, so record it as minutes when a human runs
the protocol. When an agent runs it, record the reading-effort proxy instead:

- **Diff effort**: the Java hunks a reviewer must read, and the number of files they
  must hold in mind together to establish the key relationship.
- **Athena effort**: the Athena entries (Changes plus Explorer cards) a reviewer must
  read, plus any diff they still have to open because Athena didn't cover it.

State which of the two was recorded.

## 4. Mechanical metrics (`metrics.json`)

| Metric | Meaning |
|---|---|
| `diff.substantive_java_files` | Java files with at least one changed line that isn't a copyright header. |
| `diff.copyright_only_java_files` | Java files whose only change is a copyright line: pure mechanical noise. |
| `coverage.production_java_file_coverage` | Share of changed non-test Java files that appear in at least one Athena Change. A file below 100% is an edit a reviewer can't reach from Athena at all. The UI exposes raw diff only per Change. |
| `coverage.unrepresented_production_java_files` | The files behind that shortfall, by name. |
| `athena.changes_by_kind` / `changes_by_category` | What Athena thinks happened. |
| `athena.semantic_entries_by_dimension` / `inferred_entries` | How much of the Explorer view is observed vs. inferred. |
| `athena.module_territories` / `module_dependencies` | What the Semantic Canvas would draw. |

When comparing two Athena versions, compare these per entry first. A changed number
tells you where to re-read. It isn't a verdict by itself.

## 5. Report template (per PR)

```text
PR:                <repo>#<n> — <title>
Repository:
URL:
Category:
Athena snapshot:   snapshots/<athena-sha>/<id>

Reference understanding:
Diff understanding:
Athena understanding:

Scores (diff → Athena):  What x → y · Why x → y · Impact x → y
  (one line of explanation per axis)

Effort with conventional diff:   (minutes | reading-effort proxy)
Effort with Athena:              (minutes | reading-effort proxy)

What Athena made easier:
What Athena failed to show:
Unexpected observations:
Most valuable Athena insight:
Biggest problem:

Diff → understanding gap:
  Observation:
  Conventional diff:
  Athena:
  Result:
  Evidence:
```

Cross-PR findings group observations into themes:

- Semantic grouping
- Architecture visibility
- Change impact
- Navigation/context
- Intent
- Noise reduction
- Framework understanding
- Visualization
- Incorrect inference
- Missing information

For each theme, give the number of PRs it appeared in, representative examples,
whether Athena helped, hurt or had no effect, and concrete follow-ups.
