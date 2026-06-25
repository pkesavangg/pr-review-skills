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
- `JIRA_ID` — extract by scanning title and body for pattern `(MOB|MA)-\d+` (first match)

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

## STEP 3 — Review Each PR (Parallel Subagents)

Spawn **one `general-purpose` subagent per PR**, all in a single message so they run in parallel. Each subagent owns its own worktree and runs the six review sub-skills inline — only a compact structured JSON result comes back to the orchestrator, which keeps the main context clean.

For each PR, launch an Agent with this prompt (substitute the PR's values):

```
You are reviewing PR #{PR_NUMBER} ({PR_TITLE}) for the meApp iOS project.

FIRST: `cd {WORKTREE_PATH}` — this is the PR's checked-out head. All file reads/greps must happen from this working directory so you see the PR's state, not main.

PR_META:
- PR_NUMBER: {PR_NUMBER}
- PR_TITLE: {PR_TITLE}
- BRANCH: {BRANCH}
- BASE_BRANCH: {BASE_BRANCH}
- PR_BODY: {PR_BODY}
- JIRA_ID: {JIRA_ID}

DIFF: /tmp/pr-diff-{PR_NUMBER}.patch
CHANGED_FILES: /tmp/pr-files-{PR_NUMBER}.txt
WORKTREE_PATH: {WORKTREE_PATH}

Run all six sub-skills in this order, passing PR_META, DIFF, CHANGED_FILES, and WORKTREE_PATH to each. Read and execute each skill file:
  1. .claude/skills/review-security.md
  2. .claude/skills/review-lint.md
  3. .claude/skills/review-regression.md
  4. .claude/skills/review-code-standards.md
  5. .claude/skills/review-ui-standards.md
  6. .claude/skills/review-issue-fix.md

Return ONLY a single JSON object (no prose, no code fences), with this exact shape:

{
  "pr_number": {PR_NUMBER},
  "verdicts": {
    "security": "PASS|WARN|FAIL",
    "lint": "PASS|WARN|FAIL",
    "regression": "Low|Medium|High",
    "code_standards": "PASS|WARN|FAIL",
    "ui_standards": "PASS|WARN|FAIL",
    "issue_coverage": "Complete|Partial|Missing"
  },
  "skill_outputs": {
    "security": "<verbatim markdown output of review-security.md>",
    "lint": "<verbatim markdown output of review-lint.md>",
    "regression": "<verbatim markdown output of review-regression.md>",
    "code_standards": "<verbatim markdown output of review-code-standards.md>",
    "ui_standards": "<verbatim markdown output of review-ui-standards.md>",
    "issue_coverage": "<verbatim markdown output of review-issue-fix.md>"
  },
  "findings": [
    {
      "skill": "security|lint|regression|code_standards|ui_standards|issue_coverage",
      "severity": "High|Medium|Low",
      "title": "<short title>",
      "file": "<path as shown in gh pr diff, e.g. iOS/meApp/Features/Settings/Stores/SettingsStore.swift>",
      "line": <integer line number in the NEW file>,
      "body": "<full finding detail and recommended fix>"
    }
  ]
}

Include in `findings` only issues that reference a concrete file AND line number. Issues without a line (general guidance, architectural notes) stay in `skill_outputs` but are omitted from `findings`.
```

Launch all per-PR subagents in parallel (single message, multiple Agent tool calls). Collect each subagent's JSON result before moving to Step 4.

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
| #{n} | {title} | {branch} | {MOB-XXXX or —} | PASS/WARN/FAIL | PASS/WARN/FAIL | Low/Med/High | PASS/WARN/FAIL | PASS/WARN/FAIL | Complete/Partial/Missing | APPROVED/NEEDS CHANGES/BLOCKED |

---

## PR #{number} — {title}

**Branch:** `{branch}` → `{base}`
**Jira:** [{JIRA_ID}](https://greatergoods.atlassian.net/browse/{JIRA_ID})
**PR URL:** {PR_URL}
**Changed files:** {N}

{skill_outputs.security from this PR's subagent JSON}

{skill_outputs.lint from this PR's subagent JSON}

{skill_outputs.regression from this PR's subagent JSON}

{skill_outputs.code_standards from this PR's subagent JSON}

{skill_outputs.ui_standards from this PR's subagent JSON}

{skill_outputs.issue_coverage from this PR's subagent JSON}

### Overall Verdict
**{APPROVED / NEEDS CHANGES / BLOCKED}**

{2–4 sentence summary: most important findings across all six dimensions}

---

(repeat the above block for each additional PR)
```

Each PR's block — from `## PR #{number}` through `---` — is self-contained and will be used as the individual PR comment body in Step 7.

Display the file path to the user once written.

---

## STEP 7 — Post PR Comments (Inline + Summary)

Ask the user:

> "Review saved to `{filepath}`. Post comments to the PRs?
> - `yes` — post to all PRs
> - `no` — done
> - List PR numbers (e.g. `42 87`) — post only to those"

Wait for the user's response, then act:

**If `yes` or specific PR numbers:**

For each selected PR, post comments in two phases — **both phases always run**:

### Phase A — Short summary comment

Post a compact summary as a general PR comment — **not** the full consolidated block (that lives in the report file). The summary must contain:

1. **Overall verdict** — `APPROVED` / `NEEDS CHANGES` / `BLOCKED`
2. **Status table** — one row with columns: Security · Lint · Regression · Code Standards · UI Standards · Issue Coverage
3. **1–3 sentence overview** of the most important findings
4. **Blockers / concerns** — short bullet list (omit if none); each bullet should reference a file:line when applicable

Template:

```markdown
### PR Review — **{APPROVED / NEEDS CHANGES / BLOCKED}**

| Security | Lint | Regression | Code Standards | UI Standards | Issue Coverage |
|----------|------|------------|----------------|--------------|----------------|
| {PASS/WARN/FAIL} | {PASS/WARN/FAIL} | {Low/Med/High} | {PASS/WARN/FAIL} | {PASS/WARN/FAIL} | {Complete/Partial/Missing} |

{1–3 sentence overview}

**Blockers / concerns:**
- {finding 1 with file:line}
- {finding 2 with file:line}

_Inline comments follow with specific file/line findings. Full report: `{report filepath}`._
```

Post via:
```bash
gh pr review {PR_NUMBER} --comment --body "{short summary from template above}"
```

### Phase B — Inline line comments

Use the `findings[]` array from this PR's subagent JSON (Step 3) as the source of inline comments — it already contains `{skill, severity, title, file, line, body}` for every finding with a concrete file:line. Post each as an inline comment pinned to its diff line.

**Step B1 — Get the head commit SHA:**
```bash
gh pr view {PR_NUMBER} --json headRefOid --jq '.headRefOid'
```

**Step B2 — Determine reachable diff lines:**

Get the diff hunk headers to know which line ranges are in the diff:
```bash
gh pr diff {PR_NUMBER} | grep "^@@"
```

Parse each `@@ -old_start,old_count +new_start,new_count @@` header. A line number N in the new file is **reachable** if `new_start <= N <= new_start + new_count - 1` for any hunk.

**Step B3 — Build inline comment payload:**

For each finding with a reachable line number, add it to the `comments` array. Collect findings whose lines are NOT in the diff for the fallback in Phase C.

```json
{
  "commit_id": "{HEAD_SHA}",
  "event": "COMMENT",
  "body": "Inline findings — see line comments below.",
  "comments": [
    {
      "path": "{file path as shown in gh pr diff, e.g. iOS/meApp/Features/Settings/Stores/SettingsStore.swift}",
      "line": {N},
      "side": "RIGHT",
      "body": "**[Severity] Short title**\n\n{finding detail and recommended fix}"
    }
  ]
}
```

Post via:
```bash
gh api repos/dmdbrands/meApp/pulls/{PR_NUMBER}/reviews \
  --method POST \
  --input /tmp/review-payload-{PR_NUMBER}.json
```

**Important constraints:**
- `event` must be `"COMMENT"` (never `"REQUEST_CHANGES"` — GitHub blocks that on self-owned PRs)
- Only use `line` values that fall within a diff hunk range (computed in Step B2)
- `path` must match the file path exactly as shown in `gh pr diff` output (includes `iOS/` prefix)
- Clean up: `rm -f /tmp/review-payload-{PR_NUMBER}.json`
- Skip Phase B entirely if there are no line-specific findings

### Phase C — Fallback for out-of-diff findings

If any findings could NOT be posted inline (lines outside all diff hunks), post them as one follow-up general comment mentioning the exact file:line references:
```bash
gh pr review {PR_NUMBER} --comment --body "{out-of-diff findings with file:line references}"
```

Skip Phase C if all findings were posted inline.

Confirm to the user which PRs received comments and include their URLs.

**If `no`:**

Confirm: "Review complete. Report saved to `{filepath}`."
