Stage and commit all changes with the Jira issue ID in the commit message.

Inputs available: ISSUE_ID (e.g. MA-3316), a concise description of what was done

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
   Example: `MA-3316 Add unit tests for AccountRepositoryAPI`

   Do NOT add a `Co-Authored-By` trailer or any other trailers.

4. Confirm the commit was created and show the commit hash and message.
