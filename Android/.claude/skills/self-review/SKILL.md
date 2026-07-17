---
name: self-review
description: Run all five specialist review checks (lint, regression, security, issue coverage, accessibility) on everything changed in the current task before committing the meApp Android app. Use before every commit, or when the user says "self review", "review my changes", "check before committing".
---

Review all files changed in the current task using five specialist review skills before committing.

## Instructions

### 1 — Gather context
```bash
# committed on this branch vs develop
git diff $(git merge-base HEAD origin/develop) HEAD
# staged but not committed
git diff --cached
```
Extract: **CHANGED_FILES** (`.kt`/gradle/resources), **DIFF**, **JIRA_ID** (from branch/commit, pattern `MOB-\d+`).

### 2 — Run all five reviews (in order)
- 2a — `/review-lint`
- 2b — `/review-regression`
- 2c — `/review-security`
- 2d — `/review-issue-fix`
- 2e — `/review-accessibility`

Run `/detekt-fix` first to clear auto-correctable lint.

### 3 — Aggregate & report
```
### Self-Review Summary
| Check           | Verdict                      |
|-----------------|------------------------------|
| Lint (detekt)   | PASS / WARNING / FAIL        |
| Regression Risk | Low / Medium / High          |
| Security        | PASS / WARNING / FAIL        |
| Issue Coverage  | Complete / Partial / Missing |
| Accessibility   | PASS / WARNING / FAIL        |

Overall: PASS / NEEDS FIXES
Key findings: <WARNING/FAIL items, or "None">
```

### 4 — Fix or proceed
- FAIL / High regression / Accessibility FAIL → fix, re-run the affected check.
- WARNING → fix if straightforward, else note.
- All clear → proceed to `/verify-on-emulator` (if UI) then `/commit`.
