---
name: pr-description
description: Generate a formatted pull request description ready to copy-paste into GitHub. Use whenever the user asks to write/draft/create a PR description ("write a PR description", "draft this PR", "create a PR description", "describe this PR", "what should I put in the PR description"). Also use when the user provides a GitHub PR link or PR number (e.g., "#1736", "https://github.com/org/repo/pull/1736") and asks to improve or write a description. Also use when the user is about to open a PR and needs help describing changes, even if they don't explicitly ask for it.
---

Generate a well-structured PR description based on either:
- **Current branch changes** (local workflow): git diff vs main, changed files, recent commits, ISSUE_ID from branch name
- **Existing GitHub PR** (remote workflow): PR metadata, commit history, file changes from GitHub via `gh pr view`

This skill invokes `/gen-pr-description-template` to consolidate template logic and eliminate duplication.

## Instructions

### Scenario Detection

**START HERE**: Determine which workflow applies:

**Scenario A: Local Branch** — User is on a feature branch and wants a description before creating a PR
- Branch name contains ISSUE_ID
- You have access to `git log` and `git diff`
- User hasn't provided a GitHub PR link/number
- → Follow the **Local Branch Workflow** below

**Scenario B: Existing GitHub PR** — User provides a GitHub PR link (e.g., `https://github.com/dmdbrands/meApp/pull/1736`) or PR number (e.g., `#1736`)
- User explicitly provides PR URL or number
- PR already exists on GitHub
- → Follow the **Existing PR Workflow** below

---

## Workflow A: Local Branch

1. **Gather context in parallel:**
   ```
   git log main..HEAD --oneline
   git diff main...HEAD --stat
   git diff main...HEAD
   ```

2. **Extract the ISSUE_ID** from the branch name (e.g. `MOB-3591` from `MOB-3591-enable-bpm-bluetooth-pairing`).
   
   **Error handling**: If branch doesn't follow standard format:
   - Check if ISSUE_ID appears in recent commit messages
   - Search for Jira issue references in commit body
   - Ask user explicitly: "What Jira ticket should this PR reference?" before proceeding

3. **Read any changed Swift files** that are unclear from the diff alone to understand intent:
   - If diff is >200 lines or involves protocol changes, read the file
   - If commit message mentions "refactor" or "improve", read key files for context

4. **Identify the change type** to tailor your approach:
   - **Feature**: Emphasize user benefit and new capability
   - **Refactor**: Explain architectural improvement and why it matters
   - **Bug fix**: Explain the bug impact and how the fix resolves it
   - **Test/Coverage**: Explain what new test coverage adds
   - **Dependency update**: Explain version change and any migration needs

5. **Invoke `/gen-pr-description-template`** with the following context:
   - ISSUE_ID (extracted from branch name)
   - Change type (feature, refactor, bug fix, etc.)
   - Changed files list (from git diff --stat)
   - Commit messages (from git log)
   - Relevant code excerpts (if needed to explain intent)
   - Test changes info (whether new tests were added)

6. **Output the result** as a markdown code block ready to copy-paste:
   - Do NOT push the branch or call `gh pr create`
   - User can copy the description into GitHub manually

---

## Workflow B: Existing GitHub PR

Use this workflow when the user provides a GitHub PR link or PR number.

1. **Extract PR Number**
   - From URL: `https://github.com/dmdbrands/meApp/pull/1736` → `1736`
   - From shorthand: `#1736` → `1736`

2. **Fetch PR metadata and changes:**
   ```bash
   gh pr view <PR_NUMBER> --json title,body,files,commits
   ```

3. **Extract ISSUE_ID from PR data:**
   - Check PR title for pattern `ISSUE-ID` (e.g., `MOB-3603`)
   - Check commit messages for JIRA references
   - Check existing PR body for `[ISSUE-ID]` link
   - If not found, ask user: "What Jira ticket should I reference? (e.g., MOB-3603)"

4. **Read key files if needed:**
   - Same heuristic as Workflow A: if diff is complex or intent unclear, read relevant Swift files

5. **Identify change type:**
   - Follow same logic as Workflow A (Feature, Refactor, Bug fix, Test, etc.)

6. **Invoke `/gen-pr-description-template`** with the following context:
   - ISSUE_ID (extracted from PR or user input)
   - Change type (inferred from commits and files)
   - Changed files list (from gh pr view)
   - Commit messages (from gh pr view)
   - Existing PR body (if any, for refinement vs generation)
   - Test changes info

7. **Output the result:**
   - If PR body is empty: generate full description from scratch
   - If PR body exists: improve for clarity, detail, professionalism
   - Output as markdown code block
   - Do NOT call `gh pr edit` or modify GitHub

---

## Rules

- ✅ Replace `ISSUE-ID` in both display text and URL with actual ID
- ✅ Use `/gen-pr-description-template` to generate all template sections
- ✅ Output description as markdown code block for easy copy-paste
- ❌ Do NOT push the branch or call `gh pr create`
- ❌ Do NOT call `gh pr edit` or modify PR on GitHub
- ❌ Do NOT include `Co-Authored-By` line or attribution

## Edge Cases & Error Handling

### Non-Standard Branch Names
1. Check commit messages for JIRA references (e.g., "MOB-3591")
2. Check branch name for standalone numbers
3. If unclear, ask: "What Jira ticket should I reference? (e.g., MOB-3591)"
4. Do NOT guess or invent an issue ID

### No Changes or Empty Diff
- Check current branch: `git branch --show-current`
- Verify against correct base: `git diff main...HEAD`
- If up-to-date with main: "No changes detected vs main. Are you on the right branch?"

### Very Large Diffs (20+ files)
- Let `/gen-pr-description-template` handle prioritization
- It will list key files and use "9+ other files" notation for remainder
