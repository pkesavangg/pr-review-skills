---
name: review-issue-fix
description: Verify a meApp Android change actually addresses every acceptance criterion in its linked Jira issue. Use when reviewing whether a PR fixes the ticket, or when the user says "does this fix the ticket", "check acceptance criteria". Also called by /self-review and /review-pr.
---

Confirm the diff meets the Jira acceptance criteria. Read-only.

## Instructions

1. Get the issue id from the branch/commit (`MOB-\d+`); fetch it via `/fetch-ticket` (or the Jira MCP).
2. Extract each acceptance-criterion bullet.
3. For each criterion, map to the concrete code/test change that satisfies it (`file:line`). Mark **Met / Partial / Missing**.
4. Check the change is **complete** (no half-implemented criterion) and **scoped** (no unrelated changes sneaking in).
5. Confirm tests exist for the new behaviour (not just that it compiles).
6. Report a criterion→evidence table. Verdict: Complete / Partial / Missing. List anything unaddressed.
