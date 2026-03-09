Create a git branch derived from a Jira issue ID and its summary, then transition the Jira ticket to In Progress.

Inputs available: ISSUE_ID, ISSUE_SUMMARY (from the fetched ticket details)

## Instructions

1. Derive the branch name:
   - Lowercase the summary
   - Replace spaces and special characters with hyphens
   - Trim trailing hyphens
   - Format: `{ISSUE-ID}-{slugified-summary}`
   - Example: `MA-3316-add-unit-tests-for-account-api-repository`

2. Run:
   ```
   git checkout -b {branch-name}
   ```

3. Transition the Jira ticket to **In Progress**:
   - Call `getAccessibleAtlassianResources` to retrieve the `cloudId` (if not already known)
   - Call `getTransitionsForJiraIssue` with `cloudId` and `ISSUE_ID` to get available transitions
   - Find the transition whose name contains "In Progress" (case-insensitive)
   - Call `transitionJiraIssue` with that transition's `id`
   - Confirm the status was updated

4. Confirm success and show the user the branch name and the new Jira status.
