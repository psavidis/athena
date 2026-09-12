
# Semantic Code Review

<!-- epic-tracking: sections 1-5 (Vision/Problem/Opportunity/Target User/Principles), 6 (Core Domain Model overview), 51 (Non-Goals), 55-58 (Success Criteria/Core UX Test/North Star/Long-Term Vision) are cross-cutting framing, not independently epic-shaped — not tracked as their own epics; referenced from the epics below instead. Epics: #3 GitHub Integration & Sync, #4 Semantic Change Engine, #5 Review UI & Navigation, #6 Review Context & Continuity, #7 AI Integration. -->

## 1. Product Vision

Semantic Code Review is a developer tool for performing code reviews as a human.

It provides an alternative review experience to file-oriented interfaces such as GitHub Pull Requests and GitLab Merge Requests by organizing a review around **semantic and conceptual changes** rather than files, hunks, and textual diffs.

The product does not attempt to replace GitHub or GitLab. It acts as a review layer on top of existing Git hosting platforms.

A developer opens a Pull Request in GitHub, performs the review entirely within Semantic Code Review, and synchronizes the resulting review back to GitHub.

The fundamental product principle is:

> **A code review is about understanding changes to software, not inspecting changed files.**

Files, symbols, hunks, and lines remain important, but they are treated as evidence supporting a higher-level understanding of the change.

---

# 2. Problem

Current code-review systems are primarily organized around textual differences:

```text
Pull Request
    ↓
Files
    ↓
Diffs
    ↓
Lines
```

This structure reflects how Git represents changes, but not how humans understand them.

A developer may perform a single conceptual operation that produces hundreds or thousands of textual changes.

Examples:

- Rename a class across 1,000 files.

- Move fields from one class to another.

- Reorder fields within a class.

- Extract a method.

- Move a method between classes.

- Replace an API throughout a codebase.

- Refactor several related classes.

- Generate mechanical changes across hundreds of files.

- Change one behavioral rule that affects many files.


The reviewer is forced to reconstruct the conceptual meaning manually.

For example:

```text
User.java
- String authenticationToken;

Account.java
+ String authenticationToken;
```

A human may immediately understand:

> "The authentication token was moved from User to Account."

The review tool should understand and present the same concept.

---

# 3. Product Opportunity

Semantic Code Review introduces an intermediate representation between Git and the human reviewer:

```text
Git
 ↓
Textual changes
 ↓
Semantic Change Model
 ↓
Human Review
 ↓
Review Context
 ↓
GitHub / AI
```

The product therefore becomes a **semantic layer over Git-based code review**.

Its purpose is not to hide the underlying code.

Its purpose is to make the underlying code easier to understand.

---

# 4. Target User

The primary user is a software engineer who:

- performs code reviews regularly;

- works with GitHub or GitLab;

- understands code at architectural and implementation levels;

- wants to personally understand changes before approving them;

- does not want AI to replace their judgment;

- wants AI to assist after they have established their own understanding.


The product is particularly valuable for experienced engineers reviewing:

- large Pull Requests;

- refactorings;

- migrations;

- cross-cutting changes;

- generated changes;

- API changes;

- architectural changes;

- large mechanical transformations.


---

# 5. Product Principles

## 5.1 Human-first

The human is the reviewer.

AI may assist the reviewer, but the product must never imply that AI understanding substitutes for human understanding.

---

## 5.2 Semantic-first

The primary unit of review is a **Change**, not a file.

---

## 5.3 Evidence remains accessible

Every semantic interpretation must be traceable to concrete code.

A reviewer must always be able to move:

```text
Concept
 ↓
Symbol
 ↓
File
 ↓
Diff
 ↓
Line
```

and in reverse:

```text
Line
 ↓
Diff
 ↓
File
 ↓
Symbol
 ↓
Concept
```

---

## 5.4 GitHub-compatible

The product must enhance existing workflows rather than require teams to abandon GitHub.

A reviewer should be able to use Semantic Code Review without requiring the author or other reviewers to change their workflow.

---

## 5.5 Commits are provenance, not review structure

Commits provide useful history but do not define how the reviewer should understand the change.

One commit may contain multiple conceptual changes.

Multiple commits may implement one conceptual change.

The semantic review model must therefore exist independently of commits.

---

## 5.6 AI follows human understanding

AI should receive the human's review context rather than simply receiving a raw PR diff.

The preferred workflow is:

```text
Human understands
       ↓
Human reviews
       ↓
Human records understanding
       ↓
AI receives context
       ↓
AI searches for missed concerns
```

---

# 6. Core Domain Model

The product should have an explicit semantic domain model.

```text
Repository
    │
    └── Review
          │
          ├── Intent
          │
          ├── Changes
          │     ├── Evidence
          │     ├── Symbols
          │     ├── Files
          │     ├── Relationships
          │     └── Review State
          │
          ├── Findings
          ├── Notes
          ├── Comments
          └── Review Context
```

---

# 7. Core Entity: Review

<!-- epic: #6 (Review Context & Continuity) — covers §7, 21-28, 32-33, 37 -->


A `Review` represents a human review session against a specific Pull Request revision.

It contains:

- repository;

- Pull Request;

- base revision;

- head revision;

- detected semantic changes;

- reviewer state;

- comments;

- findings;

- private notes;

- review context;

- synchronization state.


A review must be associated with a specific repository state while remaining capable of tracking semantic continuity across subsequent PR revisions.

---

# 8. Core Entity: Change

<!-- epic: #4 (Semantic Change Engine) — covers §8-13, 18-20, 44-48, 52-53 -->


A `Change` is the fundamental unit of the product.

A Change represents a meaningful conceptual transformation in the software.

Examples:

```text
Rename Customer → Account
Move authenticationToken User → Account
Extract PaymentValidator
Change retry policy
Introduce authorization check
Replace REST client
Update persistence model
```

A Change contains:

- title;

- description;

- category;

- confidence;

- affected symbols;

- affected files;

- textual evidence;

- related Changes;

- dependencies;

- review state;

- reviewer comments;

- provenance.


---

# 9. Change Categories

The initial semantic taxonomy should include:

### Mechanical

Changes that are primarily repetitive and unlikely to require individual inspection.

Examples:

- identifier replacement;

- import updates;

- formatting;

- generated files;

- trivial syntax transformations.


### Structural

Changes to the organization or structure of software.

Examples:

- moving fields;

- moving methods;

- extracting classes;

- merging classes;

- changing interfaces;

- reorganizing packages.


### Behavioral

Changes that may alter runtime behavior.

Examples:

- new conditions;

- changed control flow;

- changed validation;

- changed error handling;

- changed authorization;

- changed persistence semantics.


### Test

Changes primarily affecting tests.

### Documentation

Changes primarily affecting documentation.

### Unknown

Changes that the system cannot confidently classify.

The classification must remain advisory.

The human remains authoritative.

---

# 10. Semantic Change Detection

The system shall analyze the difference between the base and head revisions and identify candidate semantic Changes.

The engine should detect, where possible:

- renamed symbols;

- moved symbols;

- moved fields;

- moved methods;

- extracted methods;

- extracted classes;

- merged classes;

- deleted symbols;

- added symbols;

- modified behavior;

- changed dependencies;

- changed interfaces;

- changed method signatures;

- changed control flow;

- changed conditions;

- changed literals;

- changed configuration;

- mechanical transformations;

- generated changes.


The system must preserve the underlying textual diff regardless of semantic interpretation.

---

# 11. Change Grouping

Multiple textual changes that represent one conceptual operation should be grouped into a single Change.

Example:

```text
1,000 textual changes
        ↓
Rename Foo → Bar
```

Another example:

```text
User.java
Account.java
AuthenticationService.java
UserRepository.java
AccountRepository.java
17 tests
        ↓
Move authentication ownership from User → Account
```

The system should prefer fewer meaningful Changes over a large number of low-level fragments when evidence supports such grouping.

---

# 12. Exceptions

A semantic group must identify exceptions to otherwise mechanical transformations.

Example:

```text
Rename Foo → Bar

1,000 occurrences
997 equivalent
3 exceptions
```

The reviewer should be able to inspect only the exceptions.

Exceptions must remain linked to the parent Change.

---

# 13. Change Relationships

Changes may be related.

The system should model relationships such as:

```text
depends-on
causes
supports
duplicates
refines
part-of
related-to
```

Example:

```text
Move authentication state
        │
        ├── changes domain model
        ├── changes persistence
        ├── changes authentication service
        └── changes tests
```

The reviewer should be able to navigate these relationships.

---

# 14. Change Map

<!-- epic: #5 (Review UI & Navigation) — covers §14-17, 38-43, 52 Review UI -->


The Change Map is the primary navigation mechanism.

Instead of:

```text
Files
 ├── src/
 ├── test/
 └── ...
```

the reviewer initially sees:

```text
Changes

Authentication migration
    ├── Domain model
    ├── Service
    ├── Persistence
    └── Tests

API migration

Mechanical rename

Formatting
```

The file tree remains available as an alternative navigation mode.

It must not be the primary review structure.

---

# 15. Multi-Level Navigation

The reviewer must be able to move between levels of abstraction.

```text
Intent
 ↓
Conceptual Change
 ↓
Symbol
 ↓
File
 ↓
Diff
 ↓
Line
```

The UI must maintain this relationship throughout the review.

A reviewer inspecting a line should be able to determine:

> Which semantic Change does this line belong to?

Likewise, a reviewer inspecting a Change should be able to determine:

> Where is the evidence for this Change?

---

# 16. PR Understanding View

Before reviewing individual changes, the system should provide a high-level explanation of the PR.

Example:

```text
WHAT CHANGED?

Intent
Move authentication state from User to Account.

Conceptual Changes

1. Move authentication state
   4 files

2. Update authentication service
   7 files

3. Update persistence
   5 files

4. Migrate tests
   12 files

5. Rename Foo → Bar
   1,283 references

6. Formatting
   741 files
```

This view is intended to help the reviewer establish a mental model before inspecting implementation details.

---

# 17. Review Queue

The system should provide a prioritized queue of potentially meaningful areas.

Example:

```text
REVIEW QUEUE

1. AuthenticationService
   Behavioral change

2. Account model
   Structural change

3. Authorization condition
   Behavioral change

4. Foo → Bar
   Mechanical change

5. Formatting
   Mechanical change
```

The queue is guidance, not an automatic review decision.

The reviewer controls the order.

---

# 18. Review State

Every Change should have an explicit review state.

Initial states:

```text
Unseen
Understanding
Reviewed
Concern
Skipped
```

The system may later introduce additional states.

The reviewer should be able to mark a whole semantic Change as reviewed without manually opening every equivalent textual occurrence.

---

# 19. Mechanical Changes

The reviewer must be able to explicitly classify a Change as mechanical.

Example:

```text
Rename Foo → Bar

1,283 references
997 equivalent
3 exceptions

[Mark mechanical]
[Review exceptions]
[Inspect all]
```

Marking a Change mechanical should update review coverage without pretending that the code was individually inspected.

The distinction between:

> "I inspected everything"

and:

> "I understood this as a mechanical transformation"

must be preserved.

---

# 20. Review Coverage

Review coverage should be semantic rather than file-based.

Example:

```text
Behavioral changes     100%
Structural changes      92%
Mechanical changes     100%
Tests                    80%
```

The system should also be able to answer:

> Which meaningful Changes have not been reviewed?

This is more useful than:

> Which files have not been viewed?

---

# 21. Human Comments

The product must support multiple comment scopes.

### Line comment

Attached to concrete code.

### Symbol comment

Attached to a class, method, field, interface, etc.

### Change comment

Attached to a semantic Change.

### Review comment

Attached to the overall review.

This allows comments such as:

> "Why is token expiry now owned by Account?"

instead of forcing the reviewer to attach the comment to an arbitrary line.

---

# 22. Private Review Notes

Reviewers must be able to create notes that are not synchronized to GitHub.

Examples:

```text
Potential session/account boundary issue.

Check whether this affects concurrent sessions.

Come back after reviewing AccountRepository.
```

These notes are part of the reviewer's private cognitive workspace.

---

# 23. Review Bookmarks

The reviewer should be able to bookmark:

- Changes;

- symbols;

- files;

- lines;

- comments.


Bookmarks are private unless explicitly converted into a shared review artifact.

---

# 24. Impact Analysis

For a selected symbol or Change, the reviewer should be able to request:

> Show impact.

The system should display:

```text
Changed:
Account.authenticationToken

Used by:
AuthenticationService
SessionManager
AccountRepository
LoginController

Tests:
AccountTest
AuthenticationServiceTest
LoginFlowTest
```

This allows the reviewer to explore consequences rather than manually search the repository.

---

# 25. Change Trace

The reviewer should be able to follow a Change through the codebase.

Example:

```text
Move authenticationToken
        ↓
User
        ↓
Account
        ↓
AuthenticationService
        ↓
Repository
        ↓
Controller
        ↓
Tests
```

This should be a first-class interaction.

---

# 26. Behavioral Understanding

Where technically feasible, the system should identify behavioral differences between revisions.

For example:

```text
BEFORE

if (user.isActive()) {
    process();
}

AFTER

if (user.isActive() && user.hasPermission()) {
    process();
}
```

The system may represent this as:

> Added authorization requirement before processing.

The original code and diff remain directly accessible.

Semantic descriptions must never replace evidence.

---

# 27. Review Continuity

Pull Requests frequently receive additional commits after review begins.

The system must preserve semantic review state across revisions.

Example:

```text
Previous revision

✓ Authentication migration
✓ Persistence migration
⚠ Authorization semantics
```

After a new revision:

```text
Current revision

✓ Authentication migration — unchanged
✓ Persistence migration — unchanged
⚠ Authorization semantics — changed
🔴 New authorization behavior
```

The system should identify:

- unchanged reviewed Changes;

- modified reviewed Changes;

- new Changes;

- removed Changes;

- Changes whose evidence changed without changing their semantic identity.


---

# 28. Semantic Identity Across Revisions

A Change should have a stable identity where possible.

For example:

```text
Change:
Move authentication state User → Account
```

should remain the same conceptual Change even if the author modifies several commits implementing it.

This is essential for review continuity.

---

# 29. GitHub Integration

<!-- epic: #3 (GitHub Integration & Sync) — covers §29-31, 52 GitHub/Sync -->


GitHub is an external integration and synchronization boundary.

The system should import:

- repository;

- Pull Request;

- base revision;

- head revision;

- commits;

- changed files;

- textual diffs;

- existing review comments;

- review state;

- permissions.


The system should export/synchronize:

- comments;

- review comments;

- review status;

- approval;

- request changes;

- resolved discussions where supported;

- viewed-file state where supported.


---

# 30. GitHub as Source of Truth

GitHub remains authoritative for the official Pull Request.

Semantic Code Review maintains its own review model and synchronizes it with GitHub.

The product must avoid creating a workflow where the team must abandon GitHub to use it.

A developer should be able to open the Pull Request normally and then choose to review it through Semantic Code Review.

---

# 31. GitHub Projection

Every first-class operation should have a defined projection onto GitHub where appropriate.

|Semantic Review Operation|GitHub Projection|
|---|---|
|Line comment|Review comment|
|Change comment|PR/review comment|
|Review approval|Approve|
|Request changes|Request changes|
|Resolve discussion|Resolve thread|
|Mark file viewed|File viewed|
|Finalize review|Submit review|
|Private note|No projection|
|Bookmark|No projection|
|Semantic Change|Review summary/comment|

Not every internal concept needs a GitHub equivalent.

The system must preserve richer internal semantics even when GitHub cannot represent them.

---

# 32. Review Submission

At the end of a review, the reviewer should see a summary before synchronization.

Example:

```text
REVIEW SUMMARY

Reviewed:
✓ Authentication migration
✓ Persistence migration
✓ Authorization flow
✓ Tests

Classified mechanical:
✓ Foo → Bar
✓ Formatting

Concerns:
⚠ Token expiry ownership

Comments:
3

GitHub actions:
Request changes
3 review comments
1 general comment
```

The reviewer explicitly confirms submission.

---

# 33. Review Context

Review Context is a structured artifact generated from the human review.

It contains:

- PR intent;

- conceptual Changes;

- semantic classifications;

- reviewed Changes;

- skipped Changes;

- mechanical classifications;

- human comments;

- private notes where explicitly selected;

- concerns;

- assumptions;

- unresolved questions;

- relevant evidence;

- review coverage;

- review decisions.


Example:

```text
PR:
Move authentication state to Account

Human understanding:
Authentication state has been moved from User to Account
because authentication is now modeled at the account boundary.

Reviewed:
✓ Domain model
✓ Authentication service
✓ Persistence
✓ Tests

Mechanical:
✓ Foo → Bar

Human concerns:
1. Token expiry may have incorrect ownership.
2. Authorization semantics should be verified.

Unreviewed:
Migration rollback behavior.
```

---

# 34. AI Integration

<!-- epic: #7 (AI Integration) — covers §34-36, 54 -->


AI is an optional second-stage assistant.

The system should support external AI providers such as Claude and Gemini without making the product dependent on one provider.

The AI receives:

```text
Repository context
+
PR diff
+
Semantic Change Model
+
Human Review Context
```

rather than only:

```text
PR diff
```

---

# 35. AI Review Philosophy

The AI should primarily answer:

> **"Given what the human understood and reviewed, what might they have missed?"**

rather than:

> **"Review this Pull Request."**

Example:

```text
Human reviewed:

✓ Authentication migration
✓ Persistence
✓ Authorization

Human concern:

Token expiry ownership

AI findings:

1. SessionManager still assumes User owns token expiry.
2. AccountRepository does not preserve expiry during migration.
3. Integration test does not cover multiple concurrent sessions.
```

The human remains responsible for deciding whether these findings are valid.

---

# 36. AI Context Boundary

The product should make it explicit what information is sent to an AI provider.

The reviewer should be able to inspect the context before sending it.

Potential controls:

```text
Included:
✓ PR intent
✓ Semantic Changes
✓ Reviewed code
✓ Human concerns
✓ Relevant symbols

Excluded:
○ Private notes
○ Unreviewed files
○ Generated files
```

Privacy and context control should be first-class.

---

# 37. Review Session

A Review Session represents the reviewer's interaction with a Pull Request.

It records:

- navigation;

- review state;

- classifications;

- comments;

- notes;

- bookmarks;

- AI interactions;

- synchronization state.


Not all activity needs to be persisted forever.

The system should distinguish between:

### Review state

Important and durable.

### Interaction history

Useful but potentially ephemeral.

---

# 38. User Interface

The primary interface should resemble a developer tool / IDE rather than GitHub.

Suggested layout:

```text
┌────────────────────────────────────────────────────────────┐
│ PR #481   Move authentication to Account      Review: 94%  │
├──────────────┬───────────────────────────┬─────────────────┤
│ CHANGE MAP   │ CODE                      │ CONTEXT         │
│              │                           │                 │
│ Authentication│ Account.java             │ Change          │
│  ├─ Model    │                           │                 │
│  ├─ Service  │ class Account {           │ Related symbols │
│  └─ Tests    │    ...                    │                 │
│              │ }                         │ Findings        │
│ Rename       │                           │ Comments        │
│              │ ───── DIFF ─────          │                 │
│ Formatting   │                           │                 │
└──────────────┴───────────────────────────┴─────────────────┘
```

---

# 39. Primary Navigation

The primary navigation should be:

```text
Changes
Symbols
Files
```

in that order.

Files remain available because experienced developers often want direct access to them.

However, Files must not dominate the experience.

---

# 40. Review Interaction

A reviewer should be able to:

1. Understand the PR.

2. Select a conceptual Change.

3. Read its explanation.

4. Inspect affected symbols.

5. Follow relationships.

6. Inspect concrete evidence.

7. Review relevant code.

8. Mark the Change as understood/reviewed.

9. Add comments or concerns.

10. Continue to the next meaningful Change.


The interface should minimize unnecessary context switching.

---

# 41. Review Completion

A review is complete when the reviewer explicitly finalizes it.

Before completion the system should show:

```text
Reviewed Changes: 14
Mechanical Changes: 3
Skipped Changes: 1
Open Concerns: 2
Comments: 4

Semantic coverage: 96%
```

The system should never automatically approve because all changes have been classified.

Final judgment belongs to the human.

---

# 42. Search

Search must operate at multiple semantic levels.

The reviewer should be able to search:

- files;

- symbols;

- Changes;

- comments;

- review notes;

- semantic categories;

- relationships.


Example:

```text
authentication
```

may return:

```text
Change:
Move authentication state

Symbols:
AuthenticationService
Account.authenticationToken

Files:
...

Comments:
"Why is expiry now account-scoped?"
```

---

# 43. Filtering

The reviewer should be able to filter by:

- behavioral;

- structural;

- mechanical;

- tests;

- unreviewed;

- reviewed;

- concerns;

- changed since last review;

- affected module;

- symbol;

- author/commit where relevant.


---

# 44. Performance Requirement

Semantic analysis must not make the product unusably slow for large Pull Requests.

The UI should allow review to begin while deeper analysis continues.

The system should distinguish:

```text
Analyzing
Ready
Partially analyzed
Analysis failed
```

A failure in semantic analysis must never prevent access to the raw diff.

---

# 45. Graceful Degradation

The product must always preserve a usable fallback:

```text
Semantic Change Model
        ↓ unavailable
Symbol-aware diff
        ↓ unavailable
Traditional textual diff
```

The reviewer must never lose access to the actual code.

---

# 46. Large PRs

Large PRs are a primary use case.

The system must support:

- thousands of changed files;

- thousands of references;

- large generated diffs;

- bulk transformations;

- monorepositories;

- cross-module changes.


The interface must avoid rendering enormous file lists or diffs unnecessarily.

---

# 47. Review Equivalence

The system should recognize equivalent transformations.

For example:

```text
Foo → Bar
```

appearing in 1,000 files should normally be represented as one conceptual Change with many pieces of evidence.

Equivalent transformations should be collapsible.

The reviewer should be able to expand individual occurrences when necessary.

---

# 48. Generated Code

Generated changes should be identified where possible.

The system should distinguish:

```text
Source change
Generated consequence
```

The reviewer should primarily review the source change while retaining access to generated output.

---

# 49. External Tool Integration

The initial product should prioritize:

1. GitHub

2. Git repositories

3. IDE integration


Future integrations may include:

- GitLab;

- Bitbucket;

- local review workflows;

- CI systems;

- issue trackers;

- AI coding assistants.


The semantic review model must remain independent of the hosting provider.

---

# 50. IDE Integration

A future IDE integration should allow the reviewer to jump directly from:

```text
Change
```

to:

```text
IDE symbol
```

and from the IDE back into the review.

The product should eventually support a workflow where review feels like a natural extension of the developer's programming environment.

---

# 51. Non-Goals

The product is not intended to:

- replace GitHub;

- replace Git;

- become a full IDE initially;

- become a general-purpose code editor;

- automatically approve Pull Requests;

- replace human reviewers with AI;

- generate large amounts of code;

- become an AI coding agent;

- optimize around commit-message-driven review;

- force reviewers to inspect every changed line.


---

# 52. MVP Scope

The first version should focus tightly on the core product thesis.

### MVP capabilities

#### GitHub

- Connect GitHub account.

- Select repository.

- Select Pull Request.

- Import PR metadata and diff.

- Import base/head revisions.


#### Semantic engine

- Parse supported programming languages.

- Build symbol model.

- Detect basic semantic changes.

- Group related changes.

- Detect mechanical transformations.

- Detect moves and renames.

- Associate changes with files, symbols and lines.


#### Review UI

- Change Map.

- Change detail view.

- Symbol navigation.

- File/diff fallback.

- Review state.

- Comments.

- Private notes.

- Review summary.


#### Synchronization

- Sync comments.

- Sync review submission.

- Sync approval/request changes.

- Sync viewed state where supported.


---

# 53. MVP Semantic Change Types

The first implementation should prioritize high-confidence transformations:

1. Rename symbol.

2. Move symbol.

3. Move field.

4. Move method.

5. Add/remove symbol.

6. Change method signature.

7. Extract method/class.

8. Mechanical replacement.

9. Formatting-only changes.

10. Basic behavioral modifications.


The system should prefer a smaller number of highly reliable semantic classifications over ambitious but unreliable AI-generated interpretations.

---

# 54. MVP AI Integration

AI should initially be implemented as a simple optional workflow:

```text
Human Review
      ↓
Generate Review Context
      ↓
Send to Claude/Gemini
      ↓
AI identifies potential missed concerns
      ↓
Human evaluates findings
```

The AI should not control the review workflow.

---

# 55. Success Criteria

The product succeeds if an experienced engineer reviewing a large PR can say:

> "I understood this PR faster because I reviewed the changes rather than the files."

More measurable success criteria:

- Reduced time to understand a PR.

- Reduced number of files manually inspected.

- Increased ability to identify meaningful behavioral changes.

- Reduced time spent on mechanical changes.

- Higher reviewer confidence.

- Better continuity across PR revisions.

- Better AI findings after human review.

- No loss of compatibility with GitHub workflows.


---

# 56. The Core UX Test

A PR with:

```text
1,000 changed files
```

should not feel like a 1,000-file review if the actual conceptual work is:

```text
Rename Foo → Bar
Move authentication state
Change authorization behavior
Update persistence
```

The product succeeds when the reviewer can understand those four Changes first and then inspect the underlying evidence selectively.

---

# 57. Product North Star

The product should optimize for this question:

> **Can a human understand what changed in the software without reconstructing the meaning of the change from thousands of textual diffs?**

And for every piece of code:

> **Can the reviewer understand why this code changed and what conceptual change it belongs to?**

---

# 58. Long-Term Vision

The mature system becomes a semantic review layer between Git and human reasoning.

```text
                         GitHub
                            │
                            │
                         Git diff
                            │
                            ▼
                 ┌────────────────────┐
                 │ Semantic Change     │
                 │ Engine              │
                 └──────────┬─────────┘
                            │
                            ▼
                 ┌────────────────────┐
                 │ Human Review       │
                 │                    │
                 │ Understand         │
                 │ Navigate           │
                 │ Judge              │
                 │ Comment            │
                 │ Decide             │
                 └──────────┬─────────┘
                            │
                     Review Context
                            │
                            ▼
                 ┌────────────────────┐
                 │ AI Second Reviewer │
                 │                    │
                 │ Find what human    │
                 │ may have missed    │
                 └──────────┬─────────┘
                            │
                            ▼
                      Human Decision
                            │
                            ▼
                         GitHub
```

The fundamental distinction is:

> **Git understands textual history.  
> Semantic Code Review understands software change.  
> The human understands meaning.  
> AI assists the human after that understanding exists.**

That separation is the foundation of the product.