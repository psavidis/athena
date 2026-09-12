<!--
PR title = squash-merge commit title. Use Conventional Commits:

  <type>(<scope>): <description>

Types:
  feat      New functionality              feat(auth): add OAuth login
  fix       Bug fix                        fix(api): handle expired tokens
  refactor  Restructuring, no behavior change   refactor(parser): simplify tokenization
  perf      Performance improvement         perf(search): cache query results
  docs      Documentation only              docs(api): document pagination
  test      Add/change tests                test(auth): cover refresh tokens
  build     Build system/dependencies       build: upgrade React to 19
  ci        CI/CD configuration              ci: add integration test workflow
  chore     Maintenance, no other type fits  chore: clean generated files
  style     Formatting only, no logic change style: format imports
  revert    Revert a previous commit         revert: revert feat(auth)

Scope = the top-level module/folder the change is concentrated in
(e.g. api, parser, auth, worker). Omit the scope (no parentheses)
when it would just restate the type or the diff has no single owner:
  - docs: document pagination        (not docs(docs) — change is confined to docs/)
  - chore: clean generated files      (repo-wide, no single module)
  - build: upgrade React to 19        (repo-wide)
  - refactor: unify error handling across api and worker  (spans modules, no single owner)
-->

Related-to: #<!-- issue number -->

<!--
Every field below is optional. Include one only when it earns its place;
delete it otherwise. A small, well-scoped PR may need none of them — the
title plus Related-to can be the whole description.

Two notations for every field, pick whichever fits the content:
  - Simple: a single short sentence — keep it inline as "Label: sentence".
  - Complex: needs a paragraph, multiple bullets, or any structure — promote
    the label to its own heading (### Label) with a blank line, then the
    content below it. Never force multi-line content onto an inline label.

Separate each field you keep from the next with a "---" divider so the
body stays scannable. Drop the divider along with any field you delete.

Context: more elaborate than the title. Add it only if the title alone
can't communicate what changed. Keep it short.

Why: explains why the change is needed, not what it contains — a feature
is self-explanatory. Best suited to bug fixes and non-obvious enhancements,
e.g. a one-line change whose motivation isn't clear from the diff. Add it
only when the reason isn't obvious.

High-Level Changes: a map of what changed and where. Add it only when the
PR spans multiple distinct areas/files and a reviewer benefits from a map
before reading the diff. Skip it for a single-purpose change. Almost always
the complex/heading form, since it's a list by nature.

Testing: how this was verified — tests added/run, manual checks.

Risks / Follow-ups: anything the reviewer or future work should know.
-->

Context: <!-- optional — delete this line if unused -->

---

Why: <!-- optional — delete this line if unused, or promote to "### Why" + content if it needs structure -->

---

### High-Level Changes
<!-- optional — delete this whole section if unused -->

- <!-- point 1 -->
- <!-- point 2 -->

---

Testing: <!-- optional — delete this line if unused -->

---

Risks / Follow-ups: <!-- optional — delete this line if unused -->
