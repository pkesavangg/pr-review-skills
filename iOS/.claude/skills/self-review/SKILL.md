---
name: self-review
description: Run all five specialist review checks (lint, regression, security, issue coverage, accessibility) on everything changed in the current task before committing. Use this before every commit — invoke when the user says "self review", "review my changes", "check before committing", or when a task is about to be committed. Also runs automatically as part of /work-ticket.
---

Review all files changed in the current task using five specialist review skills before committing.

## Instructions

### 1 — Gather Context

Collect the full set of changes on this branch relative to `main`, plus any staged but uncommitted work:

```bash
# All changes committed on this branch vs main
git diff $(git merge-base HEAD origin/main) HEAD

# Staged but not yet committed
git diff --cached
```

If `origin/main` is not reachable, fall back to the local `main` ref:
```bash
git diff $(git merge-base HEAD main) HEAD
git diff --cached
```

Extract:
- **CHANGED_FILES** — union of modified/created `.swift` files from both outputs
- **DIFF** — the combined patch text
- **WORKTREE_PATH** — the repo root
- **JIRA_ID** — from the current branch name or most recent commit message (pattern `(MOB|MA)-\d+`)
- **PR_META** — branch name + most recent commit message as the title/body proxy

---

### 2 — Run All Five Specialist Reviews

Execute each skill in order, passing the context gathered above.

#### 2a — Lint & Formatting
Read and execute `.claude/skills/review-lint.md`

#### 2b — Regression Risk
Read and execute `.claude/skills/review-regression.md`

#### 2c — Security
Read and execute `.claude/skills/review-security.md`

#### 2d — Issue Coverage
Read and execute `.claude/skills/review-issue-fix.md`

#### 2e — Accessibility
Read and execute `.claude/skills/review-accessibility.md`

---

### 3 — Aggregate & Report

```
### Self-Review Summary

| Check           | Verdict                      |
|-----------------|------------------------------|
| Lint            | PASS / WARNING / FAIL        |
| Regression Risk | Low / Medium / High          |
| Security        | PASS / WARNING / FAIL        |
| Issue Coverage  | Complete / Partial / Missing |
| Accessibility   | PASS / WARNING / FAIL        |

**Overall verdict:** PASS / NEEDS FIXES

Key findings:
- <list any WARNING or FAIL items that need action>
- "None" if all checks passed
```

---

### 4 — Fix or Proceed

- **FAIL** or regression risk **High** or accessibility **FAIL** → fix before committing; re-run the affected check after fixing
- **WARNING only** → fix if straightforward; otherwise note and proceed
- **All PASS / Low / Complete** → confirm ready to commit and call `/commit`
