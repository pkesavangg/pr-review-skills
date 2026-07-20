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
Read and execute `.claude/skills/review-lint/SKILL.md`

#### 2b — Regression Risk
Read and execute `.claude/skills/review-regression/SKILL.md`

#### 2c — Security
Read and execute `.claude/skills/review-security/SKILL.md`

#### 2d — Issue Coverage
Read and execute `.claude/skills/review-issue-fix/SKILL.md`

#### 2e — Accessibility
Read and execute `.claude/skills/review-accessibility/SKILL.md`

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

---

### 5 — Record the commit-gate marker + report

`git commit` through Claude Code is **hard-blocked** by `scripts/commit-review-gate.sh` (wired in `iOS/.claude/settings.json`) until self-review has passed on the current working tree. This step is mandatory, and it does two things: **always** write the verdict report (so a blocked commit can tell the user exactly what failed), and write the pass-marker **only** when clean.

1. **Always** write the report — the Step 3 summary table + Key findings, including the literal `Overall verdict:` line (the gate greps it for `NEEDS FIXES`):
   ```bash
   cat > "$(git rev-parse --absolute-git-dir)/self-review-report" <<'EOF'
   <paste the Step 3 Self-Review Summary table, Overall verdict line, and Key findings here>
   EOF
   ```
2. Then, by verdict:
   - **PASS** → record the pass-marker to unlock the commit:
     ```bash
     scripts/review-fingerprint.sh > "$(git rev-parse --absolute-git-dir)/self-review-pass"
     ```
   - **NEEDS FIXES** → remove any stale pass-marker so the commit stays blocked; the report written in step 1 is what the gate shows the user:
     ```bash
     rm -f "$(git rev-parse --absolute-git-dir)/self-review-pass"
     ```

The marker is a fingerprint of the working tree, so **any further edit invalidates it** and requires re-running self-review — but `git add` alone does not, so the `self-review → stage → commit` flow works. Both files live in `.git/` (never committed, per-clone). To commit intentionally without self-review, add `--no-verify` to the `git commit`.
