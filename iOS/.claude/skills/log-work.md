---
name: log-work
description: Log time spent on the current Jira issue via the Atlassian worklog API. Use when the user says "log work", "log time", or "add worklog". Also runs as part of /work-ticket Step 9.
---

Log time on a Jira issue using Atlassian MCP tools.

Inputs available: ISSUE_ID, summary of work done across all phases, PR URL

## Instructions

Use MCP tools only — do not use CLI or curl. Tokens are managed by the MCP server config.

1. Ask the user:
   > "How much time did this task take? (e.g. 1h, 2h 30m)"

2. Call `getAccessibleAtlassianResources` to retrieve the `cloudId`

3. Call `addWorklogToJiraIssue` with:
   - `cloudId`, `issueIdOrKey`: from above
   - `timeSpent`: converted to Jira format (e.g. `1h 30m`)
   - `commentBody`: a concise but descriptive summary of what was done — cover the key implementation decisions and outcomes, not a phase-by-phase breakdown. Include the PR link at the end.

   Example format:
   ```
   Refactored X to support Y for testability. Added N unit tests covering <key scenarios>. Coverage: Z%.

   PR: https://github.com/...
   ```

4. Do NOT call `addCommentToJiraIssue` — only the worklog is needed.

5. Confirm the worklog was added successfully.
