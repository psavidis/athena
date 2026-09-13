# Semantic Change Engine

The core of the product: analyze the diff between a PR's base and head
revisions and produce `Change` objects — meaningful conceptual
transformations (rename, move, extract, behavioral change, etc.) —
instead of leaving the reviewer to reconstruct meaning from raw textual
diffs. Multiple textual edits that represent one conceptual operation are
grouped into a single Change with exceptions called out explicitly.
Classification is advisory; the human is always authoritative, and the
underlying diff remains accessible regardless of semantic interpretation.
Must degrade gracefully on unparseable input or large PRs rather than
block review.

Java-only for MVP. See Epic #4 for the full vision and scope boundary.

## Features

- `java_parsing_foundation.feature` — parsing Java source into an AST
- `java_symbol_model.feature` — building the symbol model renames/moves/extracts are detected against
- `structural_change_detection.feature` — mechanical/structural transformation detection
- `behavioral_change_detection.feature` — narrow condition/control-flow change detection
- `change_identity.feature` — identity and evidence association for a Change
- `change_grouping.feature` — grouping related textual edits into one Change, with exceptions
- `module_grouping.feature` — grouping Changes at the module level
- `graceful_degradation_fallback_chain.feature` — Semantic Model → symbol-aware diff → textual diff fallback
- `review_state_and_coverage.feature` — per-Change review state and semantic review coverage
