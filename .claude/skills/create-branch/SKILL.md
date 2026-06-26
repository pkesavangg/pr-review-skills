---
name: create-branch
description: Create a git branch from a Jira issue ID and slugified summary, then transition the ticket to In Progress. Use when the user says "create a branch", "start working on MOB-XXXX", or at the start of a new task. Also runs as part of /work-ticket Step 2.
---

Create a git branch derived from a Jira issue ID and its summary, then transition the Jira ticket to In Progress.

Inputs available: ISSUE_ID, ISSUE_SUMMARY (from the fetched ticket details)

## Instructions

1. Derive the branch name:
   - Lowercase the summary
   - Replace spaces and special characters with hyphens
   - Trim trailing hyphens
   - Format: `{ISSUE-ID}-{slugified-summary}`
   - Example: `MOB-3316-add-unit-tests-for-account-api-repository`

2. Base the branch off the latest `develop` (the up-to-date integration branch carrying Phase 2 — `phase2-dev` was merged into it). Only target `main` for 5.0.x / MA release-line hotfixes.
   ```
   git fetch origin
   git checkout develop && git pull --ff-only
   git checkout -b {branch-name}
   ```

3. Transition the Jira ticket to **In Progress**:
   - Call `getAccessibleAtlassianResources` to retrieve the `cloudId` (if not already known)
   - Call `getTransitionsForJiraIssue` with `cloudId` and `ISSUE_ID` to get available transitions
   - Find the transition whose name contains "In Progress" (case-insensitive)
   - Call `transitionJiraIssue` with that transition's `id`
   - Confirm the status was updated

4. Confirm success and show the user the branch name and the new Jira status.
