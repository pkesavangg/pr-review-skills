---
name: post-change-guard
description: Mid-session quality guard. Auto-fixes SwiftLint violations, accessibility issues, and concurrency patterns; reports security and code standards findings; triggers build if vital files changed. Use when user says "run the guard", "post-change check", "check my changes", "guard", "quality check", "fix and check", "mid-session review", "check before self-review", "fix and review". Runs after finishing any implementation task, before /self-review.
---

Run a comprehensive quality check and auto-fix pass on all current working tree changes.

## Purpose

This guard runs DURING development — after a batch of changes, before committing. It:
- **Auto-fixes**: SwiftLint violations, accessibility issues, concurrency patterns
- **Reports only**: Security findings, code standards deviations (too risky to auto-fix)
- **Triggers build**: Only when vital infrastructure files are touched

Position in workflow:
```
[Implementation complete] → /post-change-guard → /self-review → /commit
```

Do NOT replace `/self-review`. This guard cleans up as you go; self-review is the final gate.

---

## Step 1 — Gather Changed Files

Collect all Swift files modified in the current working tree (unstaged, staged, and committed on this branch):

```bash
# Uncommitted changes (unstaged + staged)
git diff --name-only -- '*.swift'
git diff --cached --name-only -- '*.swift'

# Committed on this branch vs main
git diff --name-only $(git merge-base HEAD origin/main 2>/dev/null || git merge-base HEAD main) HEAD -- '*.swift'
```

Deduplicate and filter out `DerivedData/` and `SourcePackages/`. Store as `{CHANGED_FILES}`.

If `{CHANGED_FILES}` is empty, report "No Swift files changed — nothing to check." and exit.

Also capture the full diff for review skills:
```bash
git diff $(git merge-base HEAD origin/main 2>/dev/null || git merge-base HEAD main) HEAD
git diff
git diff --cached
```
Combined patch stored as `{DIFF}`.

Store the repo root as `{WORKTREE_PATH}`:
```bash
git rev-parse --show-toplevel
```

---

## Step 2 — Detect Vital File Changes (Build Trigger Decision)

Check whether any changed file matches a vital infrastructure path. These patterns indicate changes that can break the app at compile or runtime:

```bash
# Check changed files (ALL files, not just .swift) for vital paths
git diff --name-only $(git merge-base HEAD origin/main 2>/dev/null || git merge-base HEAD main) HEAD
git diff --name-only
git diff --cached --name-only
```

**Vital path patterns** — if any changed file matches, set `{BUILD_REQUIRED}=true`:

| Path Pattern | Why It Triggers Build |
|---|---|
| `meApp/Core/DI/` | DI container — registration changes break runtime |
| `meApp/Core/Services/ServiceRegistry.swift` | Service registration order and tier assignment |
| `meApp/Domain/Repositories/*Protocol.swift` | API contract changes break all conforming types |
| `meApp/Domain/Services/*Protocol.swift` | Service protocol changes break all conforming types |
| `meApp/Data/Services/` | Service implementations — logic and DI conformance |
| `meApp/Data/API/` | API repositories — network layer |
| `meApp/Core/Network/` | HTTP stack — affects all network calls |
| `meApp/Domain/Models/DB/` | SwiftData models — schema changes |
| `meApp.xcodeproj/` | Project config — build settings, file membership |

Store `{VITAL_FILES_CHANGED}` as the list of matched vital paths (for reporting).

---

## Step 3 — Run Parallel Quality Checks

Run all four review checks concurrently. They are all read-only diff analyses with no ordering dependency:

**[PARALLEL BLOCK — run all four simultaneously]**

### 3a — SwiftLint Auto-Fix
Read and execute `.claude/skills/swiftlint/SKILL.md`

Pass scope: `{CHANGED_FILES}` as the file list.

This skill:
1. Runs `swiftlint lint --fix` on changed files (auto-corrects ~50 rules)
2. Manually fixes HIPAA rules (`no_print_or_nslog`, `no_direct_userdefaults`, `no_hardcoded_credentials`)
3. Manually fixes force operations (`force_unwrapping`, `force_cast`, `force_try`)
4. Reports all auto-fixes and manual fixes applied

Capture result as `{LINT_RESULT}`.

### 3b — Accessibility Review + Fix
Read and execute `.claude/skills/review-accessibility/SKILL.md` with `--fix` flag.

Pass: `CHANGED_FILES={CHANGED_FILES}`, `DIFF={DIFF}`, `WORKTREE_PATH={WORKTREE_PATH}`

The `--fix` mode auto-adds missing accessibility labels, hides decorative elements, adds identifiers, and fixes fixed font sizes.

Capture result as `{A11Y_RESULT}`.

### 3c — Security Review (Report Only)
Read and execute `.claude/skills/review-security/SKILL.md`

Pass: `DIFF={DIFF}`, `CHANGED_FILES={CHANGED_FILES}`, `WORKTREE_PATH={WORKTREE_PATH}`

This is report-only. Do NOT auto-fix security findings — they require human judgment.

Capture result as `{SECURITY_RESULT}`.

### 3d — Code Standards Review (Report Only)
Read and execute `.claude/skills/review-code-standards/SKILL.md`

Pass: `DIFF={DIFF}`, `CHANGED_FILES={CHANGED_FILES}`, `WORKTREE_PATH={WORKTREE_PATH}`

Report-only. DI patterns, feature structure, store conventions, string centralisation, logging, protocol naming.

Capture result as `{STANDARDS_RESULT}`.

**[END PARALLEL BLOCK]**

---

## Step 4 — Swift Concurrency Fix Pass

After the parallel block (because concurrency fixes modify files, which affects lint):

Read and execute `.claude/skills/swift-concurrency/SKILL.md`

Pass scope: files in `{CHANGED_FILES}` that are async/actor-related — specifically:
- Files in `meApp/Data/Services/`
- Files in `meApp/Data/API/`
- Files containing `async`, `await`, `@MainActor`, `actor`, `Task`, `Sendable`

This skill actively rewrites code to apply correct concurrency patterns.

Capture result as `{CONCURRENCY_RESULT}`.

If no async-related files are in scope, skip this step and set `{CONCURRENCY_RESULT}="No async files in scope — skipped."`.

---

## Step 5 — Build Check (Conditional)

Only execute this step if `{BUILD_REQUIRED}=true` from Step 2.

Read and execute `.claude/skills/build/SKILL.md`

This runs:
```bash
xcodebuild build \
  -project meApp.xcodeproj \
  -scheme meApp \
  -configuration Dev \
  -destination 'generic/platform=iOS' \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
```

Capture result as `{BUILD_RESULT}`. On build failure, stop and present build errors immediately — do not proceed to Step 6 until errors are resolved.

---

## Step 6 — Aggregate Report

Present a structured verdict table:

```
## Post-Change Guard Report

**Files checked:** {N} Swift files
**Vital infrastructure changed:** {YES — list paths / NO}

| Check | Status | Auto-Fixed | Notes |
|---|---|---|---|
| SwiftLint | PASS / WARNING / FAIL | {N} violations fixed | {summary} |
| Accessibility | PASS / WARNING / FAIL | {N} issues fixed | {summary} |
| Security | PASS / WARNING / FAIL | — (report only) | {summary} |
| Code Standards | PASS / WARNING / FAIL | — (report only) | {summary} |
| Concurrency | PASS / SKIPPED / FIXED | {N} patterns fixed | {summary} |
| Build | PASS / FAIL / SKIPPED | — | {summary} |

**Overall:** READY FOR SELF-REVIEW / NEEDS FIXES

### Auto-Fixes Applied
{bullet list of all fixes made in Steps 3a, 3b, 4}

### Action Required (Manual Fixes)
{bullet list of FAIL/WARNING findings from security and code-standards that need manual attention}

### Next Step
{if READY}: Run `/self-review` before committing.
{if NEEDS FIXES}: Fix the items listed above, then re-run `/post-change-guard`.
```
