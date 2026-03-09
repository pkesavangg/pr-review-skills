Push the current branch and create a pull request using the GitHub CLI.

Inputs available: ISSUE_ID, ISSUE_SUMMARY, list of changed files, base branch chosen by user, coverage results (if task involves unit/UI tests)

## Instructions

1. Ask the user:
   > "Which branch should I raise the PR against?"

2. Push the branch:
   ```
   git push -u origin {branch-name}
   ```

3. Create the PR using `gh pr create`:
   - Title: `{ISSUE-ID} {issue summary}`
   - Base: branch specified by user
   - Body format:

   ```
   ## Summary
   - <clear bullet points explaining what changed and why, not just what files were touched>

   ## Changes
   | File | What changed |
   |------|-------------|
   | `path/to/file.swift` | <brief description> |

   ## Test Plan
   - [ ] Build passes with no errors or warnings
   - [ ] SwiftLint passes with no errors
   - [ ] Unit tests pass
   - [ ] Manual smoke test on affected flows

   ## Coverage
   _(Include this section only if the task involves unit or UI tests)_

   | File | Coverage |
   |------|----------|
   | `path/to/SourceFile.swift` | X% |

   ## Jira
   [ISSUE-ID](https://greatergoods.atlassian.net/browse/ISSUE-ID)
   ```

   - Replace `ISSUE-ID` in both the display text and the URL with the actual issue ID (e.g. `MA-3316`).
   - Omit the **Coverage** section entirely for non-test tasks.
   - Do NOT include a `Co-Authored-By` line or any attribution in the PR body.

4. Return the PR URL to the user.

5. Transition the Jira issue to **In Review**:
   - Call `getAccessibleAtlassianResources` to retrieve the `cloudId` (if not already known)
   - Call `transitionJiraIssue` with `issueIdOrKey: ISSUE_ID` and `transition.id: "3"` (In Review)
   - Confirm the status was updated.
