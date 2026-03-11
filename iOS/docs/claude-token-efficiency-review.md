# Claude Token Efficiency Review — meApp iOS

> Reviewed: 2026-03-10 | Reviewer: Claude Sonnet 4.6 | Scope: Token usage, context handling, instruction quality

---

## 1. Overall Assessment

The setup is **functionally correct but token-heavy**. The skill chain depth in `work-ticket` and `review-pr` means a single session can load 8,000–12,000 tokens of skill instruction before reading a single line of production code. For a 200K token context window that sounds fine in isolation, but compound this with large diffs, SwiftData schema reads, multi-file code exploration, and MCP API responses — and real sessions will regularly push 60–80% context utilisation on tasks like full feature implementation or multi-PR review.

Three patterns drive most of the waste: **duplicate codebase exploration** within a single command, **repeated boilerplate** (xcodebuild commands, coverage thresholds, git diff setup) copied across 6+ files, and **template bloat** in generation skills that Claude does not actually need at runtime.

There is also one active bug in `work-ticket.md` from the previous update: a duplicate feature-slice dispatch line that the `sed` replacement introduced.

---

## 2. What Is Already Good for Token Efficiency

| Pattern | Why It Is Efficient |
|---------|-------------------|
| **Agents for heavy analysis** (`coverage-gap-finder`, `di-impact-finder`, `api-change-planner`, `gen-mock-batch`) | Spawn their own context window; do not inflate the main session |
| **SwiftLint hook** | Catches lint violations inline, avoiding a full lint review skill invocation in most cases |
| **review-issue-fix fallback** for missing JIRA_ID | Exits early rather than running MCP calls on PRs with no ticket |
| **gen-test-file coverage threshold table** | Single reference table covers all layer decisions; no external lookup needed |
| **review-regression shared input contract** (`DIFF`, `CHANGED_FILES`, `WORKTREE_PATH`) | Pre-computed inputs flow from caller to skill, avoiding repeated git commands |
| **Skill frontmatter with clear triggers** | Good descriptions on most skills mean Claude picks the right tool on first attempt, avoiding retries |
| **feature-slice** using `find` + `rg` before scaffolding | Explores repo once before generating, rather than generating and then correcting |

---

## 3. Gaps and Weaknesses

### 3.1 Critical: Double Codebase Exploration in `work-ticket`

`work-ticket.md` Step 3 runs `create-prd.md`, which does a full codebase exploration (Step 2 of create-prd: `rg -l`, `find`, reads 2–3 source files). Then Step 4 immediately does a second "Deep Brainstorm" — described as "thoroughly explore the codebase relevant to this task: Read existing files, patterns, tests, and conventions".

This means every `work-ticket` invocation reads the codebase **twice** before writing a line of code. Conservatively this is 3,000–8,000 tokens of file content loaded twice — wasted on re-reading the same files.

### 3.2 Critical: Duplicate Feature-Slice Dispatch Lines in `work-ticket`

The `sed` replacement in the previous update produced two lines both pointing to `feature-slice.md`:

```
- New feature slice or nested module: read and execute .claude/skills/feature-slice.md
- Simple top-level feature scaffold: read and execute .claude/skills/feature-slice.md for all feature scaffolding
```

This confuses the dispatcher and adds a redundant bullet that burns tokens on every `work-ticket` invocation.

### 3.3 Critical: Coverage Thresholds Defined in Three Places

The same coverage threshold table (`Data/API: 75%`, `Data/Services: 80/85%`, `Stores/Forms: 80/85%`) appears verbatim in:
- `CLAUDE.md`
- `gen-test-file.md` (Step 2)
- `verify-tests.md` (Step 5)

Every session that loads both `gen-test-file` and `verify-tests` duplicates ~150 tokens of threshold table. More importantly, if a threshold changes, it must be updated in three places.

### 3.4 Important: Self-Review Inflates Main Context Before Commit

`self-review.md` reads and executes all four review skills sequentially inside the main context. Their combined instruction content (~2,300 tokens) loads into the main window, then their combined output (4 review summaries) stays in context for the commit and PR steps that follow.

In a `work-ticket` session, self-review is Step 7. By that point, the context already contains the task description, PRD, code changes, and test output. Adding 2,300 tokens of review skill instructions plus ~500 tokens of review output at this late stage pushes the session significantly toward its limit.

Contrast: heavy analysis tasks (`coverage-gap-finder`, `di-impact-finder`) correctly offload to agents. Self-review does not.

### 3.5 Important: `review-lint` Re-reads `.swiftlint.yml` on Every Call

Step 1 of `review-lint.md` reads `.swiftlint.yml` with `cat`. This file is potentially large (100+ lines). It is read on every invocation — including every `self-review` and every PR review pass. In a `review-pr` session with 3 PRs, `.swiftlint.yml` is loaded 3 times. The content (disabled rules, thresholds, opt-in rules) changes rarely and could be referenced more efficiently.

### 3.6 Important: `review-security` Reads `AppConstants.swift` on Every Call

Step 3 reads `meApp/Core/Config/AppConstants.swift` to "understand what counts as sensitive". This file load happens on every security check — every `self-review`, every `review-pr` pass. The sensitivity rules are stable; they do not need re-reading each time.

### 3.7 Important: `create-prd` Template Block is ~350 Tokens of Static Output Format

Lines 86–186 of `create-prd.md` are a 100-line PRD markdown template. This is not an instruction — it is an output format. Claude does not need this as context in every `work-ticket` session; it only needs to be referenced when writing the PRD. The template could live in `docs/templates/prd-template.md` and the skill would just reference it by path.

### 3.8 Moderate: `gen-test-file` and `gen-mock-single` Carry Code Templates Claude Knows

`gen-test-file.md` contains a 55-line Swift code template (lines 49–104). `gen-mock-single.md` has two reference implementations totalling ~40 lines. These are already patterns Claude knows well from the project conventions in `CLAUDE.md`. Loading them as skill content adds ~400 tokens of Swift code that serves as a reminder rather than necessary instruction.

The templates are more useful as **output style guidance** (naming conventions, struct layout) than as copy-paste scaffolds.

### 3.9 Moderate: Git Diff Setup Duplicated Across Skills

The following `git diff` setup block appears independently in both `review-lint.md` (Step 0) and `self-review.md` (Step 1):

```bash
git diff $(git merge-base HEAD origin/main) HEAD
git diff --cached
```

Both compute `CHANGED_FILES` and `DIFF` from scratch. When `self-review` calls `review-lint`, `review-lint` re-derives the same diff that `self-review` already computed. This wastes ~50 tokens of instruction and one tool call per review skill that runs standalone.

### 3.10 Moderate: `work-ticket` Step 4 Brainstorm Has No Scope Bound

"Thoroughly explore the codebase relevant to this task: Read existing files, patterns, tests, and conventions" has no constraint on how many files to read or how deeply to explore. An agent executing this instruction can read 15+ files before planning, consuming 10,000+ tokens of source code in context before the first code edit. For a small bug-fix ticket this is disproportionate.

### 3.11 Minor: xcodebuild Commands Repeated Across 5 Files

The same `xcodebuild test` and `xcodebuild build` commands with identical flags appear in:
- `verify-tests.md`
- `fix-bug.md`
- `refactor.md`
- `gen-ui-test-file.md` (output note)
- `wire-navigation.md`
- `work-ticket.md` Step 6 (inline)

This is ~200 tokens of repeated CLI invocations. If flags change (e.g., a new simulator name), all 6 files need updating.

### 3.12 Minor: Long Multi-Line Frontmatter Descriptions

Several new skills have multi-sentence, multi-clause description fields:

- `fix-bug.md`: 3 sentences in description
- `wire-navigation.md`: 2 sentences + example triggers
- `gen-ui-test-file.md`: 2 sentences

Frontmatter `description` fields are loaded by Claude to select skills. They should be **one sentence + key triggers**. Verbose descriptions add tokens at skill-selection time and provide diminishing returns over concise ones.

---

## 4. Missing Optimisations

### Single-source build commands reference — doc or CLAUDE.md section

| Field | Value |
|-------|-------|
| **Purpose** | Define simulator name, xcodebuild flags, and scheme names once in `CLAUDE.md` or a dedicated `docs/build-reference.md` |
| **Trigger** | Referenced by name from skills: "Run the standard unit test command from CLAUDE.md" |
| **Why needed** | 6 files repeat the same xcodebuild invocation; one source prevents drift |

---

### Single-source coverage thresholds — remove from skill files

| Field | Value |
|-------|-------|
| **Purpose** | Remove coverage threshold tables from `gen-test-file.md` and `verify-tests.md`; reference `CLAUDE.md` as the single source |
| **Trigger** | Skills reference: "Layer thresholds are defined in CLAUDE.md" |
| **Why needed** | Three copies create maintenance burden; the thresholds are already authoritative in `CLAUDE.md` |

---

### Offload self-review to a subagent

| Field | Value |
|-------|-------|
| **Purpose** | Run the four review checks in a spawned agent that returns a structured pass/fail summary |
| **Trigger** | Called from `work-ticket` Step 7 and standalone `/self-review` |
| **Why needed** | Moves ~2,300 tokens of review skill content + output out of the main session context window, preserving room for code analysis and commit steps |

---

### Merge `work-ticket` Steps 3 and 4 into one exploration pass

| Field | Value |
|-------|-------|
| **Purpose** | Step 3 (create-prd) and Step 4 (brainstorm) both read the codebase; one pass should produce both the PRD and the implementation plan |
| **Trigger** | Internal to `work-ticket.md` |
| **Why needed** | Eliminates double codebase exploration (3,000–8,000 tokens read twice) |

---

## 5. Redundancy and Token-Waste Consolidation

### Duplicate feature-slice dispatch in `work-ticket.md` — fix immediately

Two consecutive bullets now both say "read and execute feature-slice.md". The first says "New feature slice or nested module", the second says "Simple top-level feature scaffold". Both resolve to the same file. The second line is dead weight.

**Action:** Collapse to a single line: "**Any feature scaffolding**: read and execute `.claude/skills/feature-slice.md`".

---

### Coverage thresholds in `gen-test-file.md` and `verify-tests.md`

The threshold tables in both files are identical to the table in `CLAUDE.md`. Claude already has `CLAUDE.md` in context at all times via the project instructions loader.

**Action:** Remove the threshold tables from both skill files. Replace with one line: "Use the coverage minimums from CLAUDE.md."

---

### `create-prd.md` template block

The 100-line PRD markdown template (lines 86–186) is a static output scaffold. It does not instruct behaviour — it shows Claude what the output looks like.

**Action:** Move the template to `docs/templates/prd-template.md`. Replace lines 86–186 in `create-prd.md` with: "Use the template at `docs/templates/prd-template.md`."

Saves ~350 tokens every time `create-prd` is loaded.

---

### `.swiftlint.yml` read in `review-lint` — load once per session

The file is read every time `review-lint` runs (every `self-review`, every `review-pr` pass). The content almost never changes.

**Action:** Move the lint rule guidance (lines 70–88 manual rule table) to a comment in `.swiftlint.yml` itself, or to `docs/swiftlint-rules.md`. The skill step then becomes: "Consult `docs/swiftlint-rules.md` for the manual rule reference. Run swiftlint against changed files and report." The file is read once at most, not on every invocation.

---

### `review-security.md` hardcoded `AppConstants.swift` read

Step 3 reads `AppConstants.swift` to understand sensitivity rules. These rules are stable: tokens live in Keychain, health data is sensitive, emails are PII. This does not need a file read on every invocation.

**Action:** Encode the sensitivity rules inline in the skill ("Auth tokens, refresh tokens, passwords, emails, and health metrics are sensitive and must use KeychainService") and remove the file read. Saves one tool call and ~200 tokens of file content per security review.

---

### `gen-test-file.md` and `gen-mock-single.md` code templates

The Swift code templates are patterns Claude knows well. Their value is in the **naming conventions and structure**, not in the full Swift syntax.

**Action:** Replace the full code template blocks with compact naming/structure reference tables. Saves ~400 tokens combined. Example for gen-test-file:

```
Template structure (do not copy verbatim — adapt to actual methods):
- File starts with: // Coverage target: N% (<Layer>)
- @Suite(.serialized) @MainActor struct {ClassName}Tests
- Single makeSUT() → (sut, dep1, dep2, ...)
- @Test("{method} success: {outcome}") / @Test("{method} failure: {condition}")
- Group under // MARK: - {methodName}
- Order: success → validation failures → runtime failures
```

---

## 6. Priority Improvements (Ranked by Token Impact)

| # | Improvement | Tokens Saved per Session | Effort |
|---|-------------|--------------------------|--------|
| 1 | Fix duplicate feature-slice line in `work-ticket.md` | ~50 tokens, eliminates confusion | Low |
| 2 | Merge `work-ticket` Steps 3+4 codebase exploration into one pass | 3,000–8,000 tokens (avoids double read) | Medium |
| 3 | Remove coverage threshold tables from `gen-test-file.md` and `verify-tests.md` | ~300 tokens per full work-ticket session | Low |
| 4 | Move `create-prd.md` template to external file | ~350 tokens per work-ticket session | Low |
| 5 | Offload `self-review` to a subagent (or write outputs to file) | ~2,800 tokens preserved in main context at Step 7 | Medium |
| 6 | Remove `AppConstants.swift` read from `review-security.md` | ~200 tokens per review pass, ×3 in review-pr | Low |
| 7 | Replace code templates in `gen-test-file.md` and `gen-mock-single.md` with compact references | ~400 tokens per generation session | Low |
| 8 | Add a 5-file read cap to `work-ticket` Step 4 brainstorm | Prevents 10,000+ token unbounded exploration | Low |
| 9 | Shorten multi-line frontmatter descriptions in new skills | ~100 tokens at skill-selection time | Low |
| 10 | Centralise xcodebuild commands in `CLAUDE.md` or `docs/build-reference.md` | ~200 tokens per session using verify-tests or fix-bug | Medium |

---

## 7. Proposed Token-Optimised File Changes

### Fix 1: `work-ticket.md` Step 5 — collapse duplicate feature-slice lines

**Current (after sed):**
```
- **New feature slice or nested module**: read and execute `.claude/skills/feature-slice.md` before writing any code
- **Simple top-level feature scaffold**: read and execute `.claude/skills/feature-slice.md` for all feature scaffolding (covers both simple and complex archetypes)
```

**Replace with (single line):**
```
- **Any feature scaffold**: read and execute `.claude/skills/feature-slice.md` (handles both simple top-level modules and nested/complex archetypes)
```

---

### Fix 2: `work-ticket.md` Steps 3+4 — merge into single exploration step

**Replace Steps 3 and 4 with:**

```markdown
## STEP 3 — Explore, Plan, and Generate PRD

Explore the codebase for this task in a single pass (read at most 5–8 files before planning):
- Find source files related to the feature area: `rg -l "{keyword}" meApp -g '*.swift'`
- Read the 2–3 most relevant existing source files to understand current patterns
- Find existing tests: `find meAppTests/Features/{Feature} -maxdepth 3 -type f`

Then in one operation:
1. Write the PRD to `docs/plans/{ISSUE-ID}-{slugified-title}.md` using the template at `docs/templates/prd-template.md`
2. Present the implementation plan to the user (files to create/modify/delete, test strategy, risks)

**Ask the user for approval or feedback before proceeding.** Use EnterPlanMode for the interactive plan review.
```

Saves one full codebase exploration pass (~3,000–8,000 tokens) per `work-ticket` invocation.

---

### Fix 3: `gen-test-file.md` Step 2 — remove threshold table

**Replace Step 2 with:**
```markdown
### 2 — Determine Coverage Threshold

Use the coverage minimums from `CLAUDE.md`. Note the threshold for this file's layer as a comment at the top of the generated file.
```

---

### Fix 4: `verify-tests.md` Step 5 — remove threshold table

**Replace the threshold table with:**
```markdown
### 5 — Check Coverage Thresholds

Read the coverage report. For each source file touched by this task, compare its coverage % against the layer minimums defined in `CLAUDE.md`. Mark each file ✅ or ❌.

UI layer files (`Views/`, `*View.swift`, `*Screen.swift`, `*Modifier.swift`) are excluded.
```

---

### Fix 5: `review-security.md` Step 3 — remove file read

**Replace Step 3 with:**
```markdown
### 3. Sensitive Data in Keychain vs UserDefaults / SwiftData

Per project rules, the following are always sensitive: auth tokens, refresh tokens, passwords, emails, weight/health measurements.

Check new `+` lines for:
- Sensitive fields written to `UserDefaults`
- Sensitive fields stored as `@Model` properties in SwiftData without encryption
- Auth tokens or passwords passed outside `KeychainService`

Flag as **FAIL** if sensitive data bypasses `KeychainService`.
```

---

### Fix 6: `gen-test-file.md` — replace code template with compact reference

**Replace lines 49–104 (the full Swift template) with:**

```markdown
### Template Structure

Do not copy verbatim — adapt every placeholder to the actual class and methods.

```
// Coverage target: <N>% (<Layer>)
import Testing
import Foundation
@testable import meApp

@Suite(.serialized)
@MainActor
struct <ClassName>Tests {

    private func makeSUT() -> (sut: <ClassName>, <deps...>) {
        TestDependencyContainer.reset()  // only if SUT uses @Injector
        let dep = Mock<Dep>()
        return (<ClassName>(dep: dep), dep)
    }

    // MARK: - <methodName>
    @Test("<methodName> success: <outcome>")
    func <methodName>Success() async throws { ... }

    @Test("<methodName> failure: <condition>")
    func <methodName>Failure() async throws { ... }
}

private enum TestError: Error, Equatable { case sample }
```

Ordering within each MARK group: success → validation/guard failures → runtime/network/persistence failures.
```

Saves ~200 tokens per gen-test-file invocation.

---

### Fix 7: Shorten frontmatter descriptions in new skills

**`fix-bug.md`** — replace description with:
```
description: Implement a targeted bug fix with a regression test. Use when root cause is known: "fix this bug", "apply this fix", after /debug-issue.
```

**`wire-navigation.md`** — replace description with:
```
description: Add a screen to the feature Route enum and RoutingView. Use when a screen is not reachable: "push to X", "add route for Y", "wire this screen", after /feature-slice.
```

**`gen-ui-test-file.md`** — replace description with:
```
description: Scaffold a UI test file (XCTestCase) for a screen or flow. Use when: "add UI tests for X screen", "scaffold UI test for Y flow", "generate UI tests".
```

---

### Fix 8: Add exploration cap to `work-ticket` Step 3

Add to the merged Step 3:

```
Limit codebase exploration to at most 8 file reads before writing the PRD. If the task requires understanding more than 8 files, note them in the PRD's "Open Questions" section and revisit during implementation.
```

---

## 8. Summary of Changes to Make

| File | Change | Priority |
|------|--------|----------|
| `.claude/commands/work-ticket.md` | Collapse duplicate feature-slice lines to one | P1 — bug |
| `.claude/commands/work-ticket.md` | Merge Steps 3+4 into single exploration+plan step | P1 — high impact |
| `.claude/skills/gen-test-file.md` | Remove threshold table (reference CLAUDE.md); replace full template with compact version | P2 |
| `.claude/skills/verify-tests.md` | Remove threshold table (reference CLAUDE.md) | P2 |
| `.claude/skills/review-security.md` | Remove `AppConstants.swift` read; inline sensitivity rules | P2 |
| `docs/templates/prd-template.md` | Create: extract PRD template from `create-prd.md` | P2 |
| `.claude/skills/create-prd.md` | Replace template block with reference to `docs/templates/prd-template.md` | P2 |
| `.claude/skills/gen-mock-single.md` | Replace Pattern A/B full code examples with compact structure reference | P3 |
| `.claude/skills/fix-bug.md` | Shorten frontmatter description to one sentence | P3 |
| `.claude/skills/wire-navigation.md` | Shorten frontmatter description to one sentence | P3 |
| `.claude/skills/gen-ui-test-file.md` | Shorten frontmatter description to one sentence | P3 |
| `CLAUDE.md` or `docs/build-reference.md` | Centralise xcodebuild invocations; reference from skills | P3 |

---

*End of review.*
