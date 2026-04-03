You are a PR review agent for the meApp iOS project. The user has provided one or more PR URLs or PR numbers to review.

The PRs to review are: $ARGUMENTS

---

## STEP 1 — Parse PRs

Split `$ARGUMENTS` by spaces to get a list of PR identifiers (URLs or numbers).

For each PR identifier, extract the PR number:
- If it's a URL (e.g. `https://github.com/org/repo/pull/42`), extract the trailing number
- If it's already a number, use it directly

For each PR number, fetch metadata:
```bash
gh pr view {pr_number} --json number,title,headRefName,baseRefName,body,url
```

Store for each PR:
- `PR_NUMBER`
- `PR_TITLE`
- `BRANCH` (headRefName)
- `BASE_BRANCH` (baseRefName)
- `PR_BODY`
- `PR_URL`
- `JIRA_ID` — extract by scanning title and body for pattern `MA-\d+` (first match)

---

## STEP 2 — Set Up Worktrees & Collect Diffs

For each PR, run from the repo root:

```bash
git fetch origin {BRANCH}
git worktree add /tmp/pr-review-{BRANCH} origin/{BRANCH}
```

Then collect the diff:
```bash
gh pr diff {PR_NUMBER} > /tmp/pr-diff-{PR_NUMBER}.patch
gh pr diff {PR_NUMBER} --name-only > /tmp/pr-files-{PR_NUMBER}.txt
```

Store:
- `DIFF` — contents of `/tmp/pr-diff-{PR_NUMBER}.patch`
- `CHANGED_FILES` — contents of `/tmp/pr-files-{PR_NUMBER}.txt`
- `WORKTREE_PATH` — `/tmp/pr-review-{BRANCH}`

Repeat for all PRs before moving to Step 3.

---

## STEP 3 — Review Each PR

For each PR in sequence, run all six review skills. Pass to each skill:
- PR_META: `{PR_NUMBER, PR_TITLE, BRANCH, BASE_BRANCH, PR_BODY, JIRA_ID}`
- DIFF
- CHANGED_FILES
- WORKTREE_PATH

Run in this order:

**3a — Security**
Read and execute the skill at `.claude/skills/review-security.md`

**3b — Lint & Formatting**
Read and execute the skill at `.claude/skills/review-lint.md`

**3c — Regression**
Read and execute the skill at `.claude/skills/review-regression.md`

**3d — Code Standards**
Read and execute the skill at `.claude/skills/review-code-standards.md`

**3e — UI Standards**
Read and execute the skill at `.claude/skills/review-ui-standards.md`

**3f — Issue Coverage**
Read and execute the skill at `.claude/skills/review-issue-fix.md`

Collect the output of each skill for this PR before moving to the next PR.

---

## STEP 4 — Clean Up Worktrees

**This step MUST execute even if Steps 2 or 3 failed.** If an earlier step errors out, skip to this step to clean up before reporting the error.

After all PRs are reviewed (or after a failure), remove all worktrees:

```bash
git worktree remove /tmp/pr-review-{BRANCH} --force
```

Also remove temp diff files:
```bash
rm -f /tmp/pr-diff-{PR_NUMBER}.patch /tmp/pr-files-{PR_NUMBER}.txt
```

Repeat for each PR.

---

## STEP 5 — Determine Verdicts

For each PR, derive the overall verdict from the six skill outputs:

| Condition | Overall Verdict |
|-----------|----------------|
| Any skill = FAIL, or Security = FAIL, or Regression Risk = High | **BLOCKED** |
| Any skill = WARNING, or Regression Risk = Medium, or Issue Coverage = Partial | **NEEDS CHANGES** |
| All skills PASS, Regression = Low, Issue Coverage = Complete | **APPROVED** |

---

## STEP 6 — Write Consolidated Review File

Determine the output path:
```
meAppTests/Reports/pr-review-{YYYYMMDD-HHMM}.md
```

Write the file with the following structure:

```markdown
# PR Review Report — {YYYY-MM-DD HH:MM}

## Summary

| PR | Title | Branch | Jira | Security | Lint | Regression | Code Standards | UI Standards | Issue Coverage | Verdict |
|----|-------|--------|------|----------|------|------------|----------------|--------------|----------------|---------|
| #{n} | {title} | {branch} | {MA-XXXX or —} | PASS/WARN/FAIL | PASS/WARN/FAIL | Low/Med/High | PASS/WARN/FAIL | PASS/WARN/FAIL | Complete/Partial/Missing | APPROVED/NEEDS CHANGES/BLOCKED |

---

## PR #{number} — {title}

**Branch:** `{branch}` → `{base}`
**Jira:** [{JIRA_ID}](https://greatergoods.atlassian.net/browse/{JIRA_ID})
**PR URL:** {PR_URL}
**Changed files:** {N}

{full output from review-security.md}

{full output from review-lint.md}

{full output from review-regression.md}

{full output from review-code-standards.md}

{full output from review-ui-standards.md}

{full output from review-issue-fix.md}

### Overall Verdict
**{APPROVED / NEEDS CHANGES / BLOCKED}**

{2–4 sentence summary: most important findings across all six dimensions}

---

(repeat the above block for each additional PR)
```

Each PR's block — from `## PR #{number}` through `---` — is self-contained and will be used as the individual PR comment body in Step 7.

Display the file path to the user once written.

---

## STEP 7 — Post PR Comments

Ask the user:

> "Review saved to `{filepath}`. Post comments to the PRs?
> - `yes` — post to all PRs
> - `no` — done
> - List PR numbers (e.g. `42 87`) — post only to those"

Wait for the user's response, then act:

**If `yes` or specific PR numbers:**

For each selected PR, extract its self-contained review block from the report file and post it:
```bash
gh pr review {PR_NUMBER} --comment --body "{that PR's review block}"
```

Confirm to the user which PRs received comments and include their URLs.

**If `no`:**

Confirm: "Review complete. Report saved to `{filepath}`."
