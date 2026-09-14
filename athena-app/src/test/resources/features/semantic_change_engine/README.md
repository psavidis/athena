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

Java is the first `LanguagePlugin` implementation (see the plugin
architecture: `athena-core`'s `com.athena.semantic.spi` SPI, discovered via
`ServiceLoader`) — not a hardcoded assumption. See Epic #4 for the full
vision and scope boundary.

## Features

Scenarios for this capability are split across modules, alongside whichever
module owns the step definitions that implement them:

- `athena-core/src/test/resources/features/semantic_change_engine/` —
  language-agnostic: `change_identity.feature`, `change_grouping.feature`,
  `module_grouping.feature`, `review_state_and_coverage.feature`
- `athena-plugin-java/src/test/resources/features/semantic_change_engine/` —
  Java-`LanguagePlugin`-specific: `java_parsing_foundation.feature`,
  `java_symbol_model.feature`, `structural_change_detection.feature`,
  `behavioral_change_detection.feature`
- here (`athena-app`) — `graceful_degradation_fallback_chain.feature`,
  `external_findings_as_evidence.feature` (ticket #114/#149): both
  exercise `PrAnalyzer` end to end through `PluginRegistry`-discovered
  plugins, so they need both `athena-core` and `athena-plugin-java` on the
  classpath, which only `athena-app` has.
