---
name: commit
description: Stage changed files and create a git commit with the Jira issue ID prefix. Use when the user says "commit", "save my changes", or at the end of a work session. Also runs as part of /work-ticket Step 7.
---

Stage and commit all changes with the Jira issue ID in the commit message.

Inputs available: ISSUE_ID (e.g. MOB-3316), a concise description of what was done

Note: SwiftLint has already been run in Step 4. Do not re-run it here.

## Instructions

1. Check recent commits to match the repo's commit message style:
   ```
   git log --oneline -5
   ```

2. Stage all relevant changed files (prefer specific file names over `git add -A`):
   ```
   git add {file1} {file2} ...
   ```

3. Commit using the format:
   ```
   {ISSUE-ID} {concise description of what was done}
   ```
   Example: `MOB-3316 Add unit tests for AccountRepositoryAPI`

   Do NOT add a `Co-Authored-By` trailer or any other trailers.
   (Claude Code adds one by default — this skill intentionally overrides that behavior. The project does not use attribution trailers.)

4. Confirm the commit was created and show the commit hash and message.
