---
name: review-issue-fix
description: Verify that a PR's code changes and tests actually address all acceptance criteria in the linked Jira issue. Use when reviewing a PR for issue completeness, or when the user says "check issue coverage", "does this fix the ticket", "verify acceptance criteria". Also called automatically by /self-review and /review-pr.
---

Verify the PR actually addresses the Jira issue it claims to fix.

Inputs available: PR_META (number, title, body, branch), DIFF (full patch text), CHANGED_FILES (list), WORKTREE_PATH, JIRA_ID (extracted from PR title/body, e.g. MOB-3316)

## Instructions

### 1 — Extract Jira Issue ID

If JIRA_ID was not already extracted by the caller:
- Scan PR_META.title and PR_META.body for the pattern `(MOB|MA)-\d+`
- Use the first match found
- If no Jira ID found: output "No Jira issue linked — skipping issue coverage check" and exit this skill

---

### 2 — Fetch the Jira Issue

Use MCP tools only — do not use CLI or curl.

1. Call `getAccessibleAtlassianResources` to retrieve the `cloudId`
2. Call `getJiraIssue` with the `cloudId` and JIRA_ID

Extract and display:
- **Title** / **Summary**
- **Issue Type** (Bug / Story / Task / Sub-task)
- **Description** — the full text
- **Acceptance Criteria** — look in the description for a section labelled "Acceptance Criteria", "AC", "Definition of Done", or numbered/bulleted requirements

If the description has no explicit AC section, derive implicit criteria from the issue type:
- **Bug**: The bug described must be fixed + there should be a test that would have failed before the fix
- **Story/Task**: The described feature must be implemented and verifiable

---

### 3 — Map Criteria to Code Changes

For each acceptance criterion (or implicit requirement):

**a) Find relevant code in the diff**
- Search the diff `+` lines for logic that addresses this criterion
- If not obvious in the diff, search the worktree for related files:
  ```bash
  rg -l "{keyword from AC}" {WORKTREE_PATH}/meApp -g '*.swift'
  ```

**b) Find relevant test coverage**
- Search `meAppTests/` in the worktree for test methods that cover this criterion
- For bug fixes specifically: is there a test that would have **failed** on the original code and **passes** with the fix? This is the gold standard.

**c) Assign coverage status:**
- **Covered** — criterion is addressed in code AND verified by a test
- **Partial** — criterion is addressed in code but no corresponding test, OR test exists but doesn't fully verify the criterion
- **Missing** — no code change or test addresses this criterion

---

### 3b — Test Coverage Minimum for New Functions

Count the number of new function/method definitions added in the diff (lines matching `^\+.*func ` in non-test files). If **5 or more new functions** are added and the diff contains **zero new test functions** (no `@Test`, no `func test` additions in `meAppTests/`):

Flag as `[High] {N} new functions added with no unit tests. Pure static functions and conversion utilities are the highest-priority candidates for unit testing (roundtrip fidelity, boundary conditions, negative inputs). Recommend adding a test file at meAppTests/Features/{Feature}/{TypeName}Tests.swift`.

This check is **in addition to** the AC-driven coverage check — it catches test debt even when the Jira ticket doesn't explicitly list "write unit tests" as an acceptance criterion.

---

### 4 — Assess Completeness

After mapping all criteria:
- Count Covered / Partial / Missing
- Check edge cases implied by the issue that may not be listed as explicit AC:
  - For bugs: is the fix only for the happy path, or also for the edge cases that triggered the bug?
  - For features: are error states (network failure, empty data, validation errors) handled?

---

## Output

```
### Issue Coverage Review

**Jira:** [{JIRA_ID}](https://greatergoods.atlassian.net/browse/{JIRA_ID}) — {title}
**Type:** {Bug / Story / Task}

#### Acceptance Criteria

| # | Criterion | Code Change | Test Coverage | Status |
|---|-----------|-------------|---------------|--------|
| 1 | … | {file or "none"} | {test method or "none"} | Covered / Partial / Missing |
| 2 | … | … | … | … |

**Uncovered edge cases:**
- {description of edge case not addressed, or "None identified"}

**Issue coverage verdict:** Complete / Partial / Missing

Rules:
- Complete = all criteria Covered, no significant edge cases missed
- Partial = some criteria Covered, some Partial/Missing, or edge cases not handled
- Missing = no criteria are Covered, or the PR does not appear to address the stated issue
```
