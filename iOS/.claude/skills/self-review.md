Review all files changed in the current task using the four specialist review skills before committing.

## Instructions

### 1 — Gather Context

Identify changed files and the full diff:
```bash
git diff --name-only HEAD~1 HEAD
git diff HEAD~1 HEAD
```

Extract:
- **CHANGED_FILES** — list of modified/created Swift files
- **DIFF** — the full patch text
- **PR_META** — use the current branch name and recent commit message as the title/body proxy
- **WORKTREE_PATH** — the repo root (e.g. `/Users/kesavan/meApp-1`)
- **JIRA_ID** — extracted from the branch name or commit message (e.g. `MA-3316`)

---

### 2 — Run All Four Specialist Reviews

Execute each skill in order, passing the context gathered above:

#### 2a — Lint & Formatting
Read and execute `.claude/skills/review-lint.md`

#### 2b — Regression Risk
Read and execute `.claude/skills/review-regression.md`

#### 2c — Security
Read and execute `.claude/skills/review-security.md`

#### 2d — Issue Coverage
Read and execute `.claude/skills/review-issue-fix.md`

---

### 3 — Aggregate & Report

Summarise all findings in one block:

```
### Self-Review Summary

| Check             | Verdict         |
|-------------------|-----------------|
| Lint              | PASS / WARNING / FAIL |
| Regression Risk   | Low / Medium / High |
| Security          | PASS / WARNING / FAIL |
| Issue Coverage    | Complete / Partial / Missing |

**Overall verdict:** PASS / NEEDS FIXES

Key findings:
- <list any WARNING or FAIL items that need action>
- "None" if all checks passed
```

### 4 — Fix or Proceed

- If any check is **FAIL** or regression risk is **High**: fix the issue before proceeding to commit
- If only **WARNING** items remain: use judgement — fix if straightforward, otherwise note and proceed
- If all **PASS / Low / Complete**: confirm the code is ready to commit
